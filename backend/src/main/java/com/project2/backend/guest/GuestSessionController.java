package com.project2.backend.guest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class GuestSessionController {
    @GetMapping("/api/v1/session")
    public ResponseEntity<Void> establishSession() {
        return ResponseEntity.noContent().build();
    }
}
