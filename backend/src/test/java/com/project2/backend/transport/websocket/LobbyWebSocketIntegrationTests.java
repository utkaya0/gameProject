package com.project2.backend.transport.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyWebSocketIntegrationTests {
    @Value("${local.server.port}")
    private int port;

    @Test
    @Timeout(20)
    void memberReceivesJoinAndLeaveEventsOverStomp() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        HttpResponse<String> created = post(http, base + "/api/v1/lobbies",
                "{\"displayName\":\"Ada\",\"maxPlayers\":2}", null);
        assertEquals(201, created.statusCode());
        String code = created.headers().firstValue("Location").orElseThrow();
        code = code.substring(code.lastIndexOf('/') + 1);
        String hostCookie = created.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];

        Frames frames = new Frames();
        WebSocket socket = http.newWebSocketBuilder()
                .header("Cookie", hostCookie)
                .header("Origin", "http://localhost:5173")
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/ws"), frames).join();
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n\0", true).join();
            assertTrue(frames.next().startsWith("CONNECTED"));
            socket.sendText("SUBSCRIBE\nid:lobby\ndestination:/topic/lobbies/" + code
                    + "\n\n\0", true).join();

            HttpResponse<String> joined = post(http, base + "/api/v1/lobbies/" + code + "/join",
                    "{\"displayName\":\"Bora\"}", null);
            assertEquals(200, joined.statusCode());
            String playerCookie = joined.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            String joinEvent = frames.next();
            assertTrue(joinEvent.startsWith("MESSAGE"));
            assertTrue(joinEvent.contains("PLAYER_JOINED"));
            assertTrue(joinEvent.contains("\"sequence\":1"));

            HttpResponse<String> left = post(http, base + "/api/v1/lobbies/" + code + "/leave",
                    "", playerCookie);
            assertEquals(204, left.statusCode());
            String leaveEvent = frames.next();
            assertTrue(leaveEvent.startsWith("MESSAGE"));
            assertTrue(leaveEvent.contains("PLAYER_LEFT"));
            assertTrue(leaveEvent.contains("\"sequence\":2"));
        } finally {
            socket.abort();
        }
    }

    @Test
    @Timeout(20)
    void guestOutsideLobbyCannotReceiveItsEvents() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;
        HttpResponse<String> created = post(http, base + "/api/v1/lobbies",
                "{\"displayName\":\"Ada\",\"maxPlayers\":2}", null);
        assertEquals(201, created.statusCode());
        String code = created.headers().firstValue("Location").orElseThrow();
        code = code.substring(code.lastIndexOf('/') + 1);
        HttpResponse<String> outsider = post(http, base + "/api/v1/single-games", "{}", null);
        String outsiderCookie = outsider.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        Frames frames = new Frames();
        WebSocket socket = http.newWebSocketBuilder()
                .header("Cookie", outsiderCookie)
                .header("Origin", "http://localhost:5173")
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/ws"), frames).join();
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n\0", true).join();
            assertTrue(frames.next().startsWith("CONNECTED"));
            socket.sendText("SUBSCRIBE\nid:foreign\ndestination:/topic/lobbies/" + code + "\n\n\0", true).join();
            assertEquals(200, post(http, base + "/api/v1/lobbies/" + code + "/join",
                    "{\"displayName\":\"Bora\"}", null).statusCode());
            String received = frames.poll(2);
            assertTrue(received == null || !received.startsWith("MESSAGE"),
                    "A nonmember received a lobby event: " + received);
        } finally {
            socket.abort();
        }
    }

    private HttpResponse<String> post(HttpClient http, String url, String body, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class Frames implements WebSocket.Listener {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private final StringBuilder current = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public synchronized CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            current.append(data);
            int end;
            while ((end = current.indexOf("\0")) >= 0) {
                String frame = current.substring(0, end).stripLeading();
                current.delete(0, end + 1);
                if (!frame.isEmpty()) queue.add(frame);
            }
            socket.request(1);
            return null;
        }

        private String next() throws InterruptedException {
            String frame = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "Expected a STOMP frame");
            return frame;
        }

        private String poll(int seconds) throws InterruptedException {
            return queue.poll(seconds, TimeUnit.SECONDS);
        }
    }
}
