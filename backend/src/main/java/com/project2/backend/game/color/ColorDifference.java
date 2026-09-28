package com.project2.backend.game.color;

/** W3C sRGB -> D50 Lab conversion and Sharma/Wu/Dalal CIEDE2000 (kL=kC=kH=1). */
public final class ColorDifference {
    private static final double POW_25_7 = Math.pow(25, 7);
    private static final double D50_X = 0.3457 / 0.3585;
    private static final double D50_Z = (1.0 - 0.3457 - 0.3585) / 0.3585;
    private static final double EPSILON = 216.0 / 24389.0;
    private static final double KAPPA = 24389.0 / 27.0;

    private ColorDifference() {
    }

    public static LabColor toLab(SrgbColor color) {
        if (color == null) {
            throw new IllegalArgumentException("Color is required");
        }
        double r = linearize(color.red() / 255.0);
        double g = linearize(color.green() / 255.0);
        double b = linearize(color.blue() / 255.0);

        // sRGB linear light to XYZ, relative to D65 (W3C CSS Color 4).
        double x65 = 506752.0 / 1228815 * r + 87881.0 / 245763 * g + 12673.0 / 70218 * b;
        double y65 = 87098.0 / 409605 * r + 175762.0 / 245763 * g + 12673.0 / 175545 * b;
        double z65 = 7918.0 / 409605 * r + 87881.0 / 737289 * g + 1001167.0 / 1053270 * b;

        // Bradford chromatic adaptation from D65 to D50.
        double x50 = 1.0479297925449969 * x65 + 0.022946870601609652 * y65 - 0.05019226628920524 * z65;
        double y50 = 0.02962780877005599 * x65 + 0.9904344267538799 * y65 - 0.017073799063418826 * z65;
        double z50 = -0.009243040646204504 * x65 + 0.015055191490298152 * y65 + 0.7518742814281371 * z65;

        double fx = labCurve(x50 / D50_X);
        double fy = labCurve(y50);
        double fz = labCurve(z50 / D50_Z);
        return new LabColor(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz));
    }

    public static double deltaE00(LabColor first, LabColor second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both Lab colors are required");
        }
        double c1 = Math.hypot(first.a(), first.b());
        double c2 = Math.hypot(second.a(), second.b());
        double meanC = (c1 + c2) / 2;
        double meanC7 = Math.pow(meanC, 7);
        double g = 0.5 * (1 - Math.sqrt(meanC7 / (meanC7 + POW_25_7)));
        double a1Prime = (1 + g) * first.a();
        double a2Prime = (1 + g) * second.a();
        double c1Prime = Math.hypot(a1Prime, first.b());
        double c2Prime = Math.hypot(a2Prime, second.b());
        double h1Prime = hueDegrees(first.b(), a1Prime);
        double h2Prime = hueDegrees(second.b(), a2Prime);

        double deltaL = second.lightness() - first.lightness();
        double deltaC = c2Prime - c1Prime;
        double deltaHueDegrees = 0;
        if (c1Prime * c2Prime != 0) {
            deltaHueDegrees = h2Prime - h1Prime;
            if (deltaHueDegrees > 180) deltaHueDegrees -= 360;
            else if (deltaHueDegrees < -180) deltaHueDegrees += 360;
        }
        double deltaH = 2 * Math.sqrt(c1Prime * c2Prime) * sinDegrees(deltaHueDegrees / 2);

        double meanL = (first.lightness() + second.lightness()) / 2;
        double meanCPrime = (c1Prime + c2Prime) / 2;
        double meanHue;
        if (c1Prime * c2Prime == 0) {
            meanHue = h1Prime + h2Prime;
        } else if (Math.abs(h1Prime - h2Prime) <= 180) {
            meanHue = (h1Prime + h2Prime) / 2;
        } else if (h1Prime + h2Prime < 360) {
            meanHue = (h1Prime + h2Prime + 360) / 2;
        } else {
            meanHue = (h1Prime + h2Prime - 360) / 2;
        }

        double t = 1 - 0.17 * cosDegrees(meanHue - 30)
                + 0.24 * cosDegrees(2 * meanHue)
                + 0.32 * cosDegrees(3 * meanHue + 6)
                - 0.20 * cosDegrees(4 * meanHue - 63);
        double deltaTheta = 30 * Math.exp(-Math.pow((meanHue - 275) / 25, 2));
        double meanCPrime7 = Math.pow(meanCPrime, 7);
        double rC = 2 * Math.sqrt(meanCPrime7 / (meanCPrime7 + POW_25_7));
        double sL = 1 + 0.015 * Math.pow(meanL - 50, 2) / Math.sqrt(20 + Math.pow(meanL - 50, 2));
        double sC = 1 + 0.045 * meanCPrime;
        double sH = 1 + 0.015 * meanCPrime * t;
        double rT = -sinDegrees(2 * deltaTheta) * rC;

        double lTerm = deltaL / sL;
        double cTerm = deltaC / sC;
        double hTerm = deltaH / sH;
        return Math.sqrt(Math.max(0, lTerm * lTerm + cTerm * cTerm + hTerm * hTerm + rT * cTerm * hTerm));
    }

    private static double linearize(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double labCurve(double value) {
        return value > EPSILON ? Math.cbrt(value) : (KAPPA * value + 16) / 116;
    }

    private static double hueDegrees(double b, double a) {
        if (a == 0 && b == 0) return 0;
        double angle = Math.toDegrees(Math.atan2(b, a));
        return angle < 0 ? angle + 360 : angle;
    }

    private static double sinDegrees(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cosDegrees(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }
}
