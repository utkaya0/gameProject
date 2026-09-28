package com.project2.backend.game.color;

/** CIE Lab values relative to the D50 reference white. */
public record LabColor(double lightness, double a, double b) {
    public LabColor {
        if (!Double.isFinite(lightness) || !Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("Lab components must be finite");
        }
    }
}
