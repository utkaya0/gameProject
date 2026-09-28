package com.project2.backend.guest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public final class GuestSessionFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "guestId";
    private static final String COOKIE_NAME = "project_guest";
    private final GuestSessionService sessions;
    private final boolean trustLoopbackProxy;

    public GuestSessionFilter(GuestSessionService sessions,
                              @Value("${app.security.trust-loopback-proxy:false}") boolean trustLoopbackProxy) {
        this.sessions = sessions;
        this.trustLoopbackProxy = trustLoopbackProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/v1/session")
                && !path.startsWith("/api/v1/lobbies")
                && !path.startsWith("/api/v1/single-games")
                && !path.startsWith("/api/v1/games/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cookieValue = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }
        GuestSessionService.Resolution session = sessions.resolve(cookieValue);
        request.setAttribute(ATTRIBUTE, session.guestId());
        if (session.newSession()) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, session.guestId().toString())
                    .httpOnly(true).sameSite("Lax").secure(request.isSecure() ||
                            (trustLoopbackProxy && isLoopback(request.getRemoteAddr())
                                    && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))))
                    .path("/api/v1").maxAge(Duration.ofDays(7)).build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        chain.doFilter(request, response);
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "0:0:0:0:0:0:0:1".equals(address) || "::1".equals(address);
    }
}
