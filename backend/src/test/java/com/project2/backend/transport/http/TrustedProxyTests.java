package com.project2.backend.transport.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.trust-loopback-proxy=true")
@AutoConfigureMockMvc
class TrustedProxyTests {
    @Autowired private MockMvc mvc;

    @Test
    void secureCookieUsesForwardedHttpsOnlyFromLoopbackProxy() throws Exception {
        mvc.perform(get("/api/v1/session").header("X-Forwarded-Proto", "https")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; }))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
        mvc.perform(get("/api/v1/session").header("X-Forwarded-Proto", "https")
                        .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; }))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));
    }
}
