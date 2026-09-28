package com.project2.backend.game.color;

import java.security.SecureRandom;

public final class ColorGenerator {
    private final SecureRandom random = new SecureRandom();

    public SrgbColor next() {
        int rgb = random.nextInt(1 << 24);
        return new SrgbColor((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF);
    }
}
