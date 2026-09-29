package com.project2.backend.lobby;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LobbyControllerTests {
    @Autowired private MockMvc mvc;

    @Test
    void defaultsToEightPlayersWhenCapacityIsOmitted() throws Exception {
        mvc.perform(post("/api/v1/lobbies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxPlayers").value(8));
    }

    @Test
    void separateGuestsJoinAndHostPermissionIsEnforced() throws Exception {
        var created = mvc.perform(post("/api/v1/lobbies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\",\"maxPlayers\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.yourRole").value("HOST"))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        String code = location.substring(location.lastIndexOf('/') + 1);
        Cookie hostCookie = created.getResponse().getCookie("project_guest");
        assertNotNull(hostCookie);

        var joined = mvc.perform(post("/api/v1/lobbies/" + code + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Bora\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourRole").value("PLAYER"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andReturn();
        Cookie playerCookie = joined.getResponse().getCookie("project_guest");
        assertNotNull(playerCookie);
        assertNotEquals(hostCookie.getValue(), playerCookie.getValue());

        mvc.perform(get("/api/v1/lobbies/" + code).cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));
        mvc.perform(post("/api/v1/lobbies/" + code + "/start").cookie(playerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));
        mvc.perform(post("/api/v1/lobbies/" + code + "/start").cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRound").value(1));
        mvc.perform(post("/api/v1/lobbies/" + code + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Cem\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_IN_PROGRESS"));
        mvc.perform(post("/api/v1/lobbies/" + code + "/leave").cookie(hostCookie))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/lobbies/" + code).cookie(playerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourRole").value("HOST"));
    }

}
