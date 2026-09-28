package com.project2.backend.game.color;

public record SrgbColor(int red, int green, int blue) {
    public SrgbColor {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB channels must be in 0..255");
        }
    }

    public static SrgbColor parse(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Color must be #RRGGBB");
        }
        return new SrgbColor(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        );
    }

    public String toHex() {
        return "#%02X%02X%02X".formatted(red, green, blue);
    }
}
