package com.project2.backend.transport.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApiProtectionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiProtectionFilter.class);
    private static final int MAX_BODY_BYTES = 4096;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Set<String> allowedOrigins;
    private final boolean trustLoopbackProxy;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ApiProtectionFilter(@Value("${app.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080}") String origins,
                               @Value("${app.security.trust-loopback-proxy:false}") boolean trustLoopbackProxy) {
        allowedOrigins = Set.copyOf(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        this.trustLoopbackProxy = trustLoopbackProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        boolean trustedProxy = trustLoopbackProxy && isLoopback(request.getRemoteAddr());
        if (request.isSecure() || (trustedProxy && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        String method = request.getMethod();
        if (method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")) {
            String path = request.getRequestURI();
            if (method.equals("GET") && !path.equals("/api/v1/health")) {
                String ip = clientAddress(request, trustedProxy);
                int limit = path.equals("/api/v1/session") ? 60 : 1200;
                if (!admit(ip + (path.equals("/api/v1/session") ? ":session" : ":read"), limit)) {
                    response.setHeader("Retry-After", "60");
                    reject(response, 429, "RATE_LIMITED");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ((origin != null && !allowedOrigins.contains(origin)) || "cross-site".equalsIgnoreCase(fetchSite)) {
            reject(response, 403, "ORIGIN_NOT_ALLOWED");
            return;
        }
        String path = request.getRequestURI();
        boolean creation = method.equals("POST") && (path.equals("/api/v1/lobbies") || path.equals("/api/v1/single-games"));
        String ip = clientAddress(request, trustedProxy);
        if (!admit(ip + (creation ? ":create" : ":write"), creation ? 20 : 180)) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "RATE_LIMITED");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "REQUEST_TOO_LARGE");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            reject(response, 413, "REQUEST_TOO_LARGE");
            return;
        }
        chain.doFilter(new BodyRequest(request, body), response);
        if (response.getStatus() >= 500) {
            log.warn("API request failed: method={} path={} status={}", method, path, response.getStatus());
        }
    }

    private boolean admit(String key, int limit) {
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        synchronized (window) {
            Instant now = Instant.now();
            if (window.started.plus(WINDOW).isBefore(now)) {
                window.started = now;
                window.count = 0;
            }
            return ++window.count <= limit;
        }
    }

    private String clientAddress(HttpServletRequest request, boolean trustedProxy) {
        if (trustedProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                String first = forwarded.split(",", 2)[0].trim();
                if (first.length() <= 45 && first.matches("[0-9a-fA-F:.]+")) return first;
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "0:0:0:0:0:0:0:1".equals(address) || "::1".equals(address);
    }

    @Scheduled(fixedDelay = 60_000)
    public void removeOldWindows() {
        Instant cutoff = Instant.now().minus(WINDOW.multipliedBy(2));
        windows.entrySet().removeIf(entry -> entry.getValue().started.isBefore(cutoff));
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        log.warn("API request rejected: code={} status={}", code, status);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String detail = switch (code) {
            case "RATE_LIMITED" -> "Too many requests; try again shortly";
            case "REQUEST_TOO_LARGE" -> "Request body is too large";
            default -> "Request origin is not allowed";
        };
        response.getWriter().write("{\"code\":\"" + code + "\",\"title\":\"" + code
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }

    private static final class Window {
        private Instant started = Instant.now();
        private int count;
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return input.read(); }
            };
        }

        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
