package com.project2.backend.transport.http;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiProtectionIntegrationTests {
    @Autowired private MockMvc mvc;

    @Test
    void rejectsForeignOriginsAndLargeBodies() throws Exception {
        mvc.perform(post("/api/v1/lobbies").header("Origin", "https://attacker.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/lobbies").header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/lobbies").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + " ".repeat(4097) + "}"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void repeatedCreateKeyReturnsSameLobbyAndChangedPayloadConflicts() throws Exception {
        String path = "/api/v1/lobbies";
        String key = "same-lobby-request-123";
        var first = mvc.perform(post(path).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie cookie = first.getResponse().getCookie("project_guest");
        assertNotNull(cookie);
        String location = first.getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(post(path).cookie(cookie).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(location, result.getResponse().getHeader("Location")));
        mvc.perform(post(path).cookie(cookie).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Bora\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void limitsLobbyCreationPerClientAddress() throws Exception {
        String address = "192.0.2.53";
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/api/v1/lobbies").with(request -> { request.setRemoteAddr(address); return request; })
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/lobbies").with(request -> { request.setRemoteAddr(address); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void repeatedSingleGameStartKeyReturnsSameGame() throws Exception {
        var first = mvc.perform(post("/api/v1/single-games").header("Idempotency-Key", "same-single-game-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        Cookie cookie = first.getResponse().getCookie("project_guest");
        assertNotNull(cookie);
        String location = first.getResponse().getHeader("Location");
        mvc.perform(post("/api/v1/single-games").cookie(cookie).header("Idempotency-Key", "same-single-game-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(result -> assertEquals(location, result.getResponse().getHeader("Location")));
    }
}
