package com.project2.backend.game.session;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SingleGameControllerTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void createsReadableGameAndReturnsStableErrors() throws Exception {
        String location = mvc.perform(post("/api/v1/single-games"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.gameType").value("COLOR_GUESS"))
                .andExpect(jsonPath("$.mode").value("SINGLE_PLAYER"))
                .andExpect(jsonPath("$.phase").value("PREVIEW"))
                .andExpect(jsonPath("$.gameData.targetColor").exists())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRound").value(1));

        mvc.perform(post(location + "/continue"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_READY"));

        mvc.perform(put(location + "/rounds/1/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guessColor\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUESS"));

        mvc.perform(put(location + "/rounds/1/submission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guess\":{\"color\":\"red\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GUESS"));

        mvc.perform(post("/api/v1/single-games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"COLOR_GUESS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("COLOR_GUESS"));

        mvc.perform(post("/api/v1/single-games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_GAME_TYPE"));

        mvc.perform(get("/api/v1/games/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }
}
