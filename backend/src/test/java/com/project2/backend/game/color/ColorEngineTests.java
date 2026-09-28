package com.project2.backend.game.color;

import com.project2.backend.common.GameConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ColorEngineTests {
    @Test
    void parsesOnlyFullHexColorsAndNormalizesOutput() {
        assertEquals("#3A7FD5", SrgbColor.parse("#3a7fd5").toHex());
        for (String invalid : new String[]{"3A7FD5", "#FFF", "#1234567", "#GG0000", "#12 456", ""}) {
            assertThrows(IllegalArgumentException.class, () -> SrgbColor.parse(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> SrgbColor.parse(null));
        assertThrows(IllegalArgumentException.class, () -> new SrgbColor(-1, 0, 0));
    }

    @Test
    void convertsSrgbNeutralsToD50Lab() {
        LabColor black = ColorDifference.toLab(SrgbColor.parse("#000000"));
        LabColor white = ColorDifference.toLab(SrgbColor.parse("#FFFFFF"));
        assertEquals(0, black.lightness(), 1e-10);
        assertEquals(100, white.lightness(), 1e-6);
        assertEquals(0, white.a(), 1e-5);
        assertEquals(0, white.b(), 1e-5);
    }

    @Test
    void matchesSharmaWuDalalReferencePairs() {
        // Source: https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/dataNprograms/ciede2000testdata.txt
        String data = """
                50.0000 2.6772 -79.7751 50.0000 0.0000 -82.7485 2.0425
                50.0000 3.1571 -77.2803 50.0000 0.0000 -82.7485 2.8615
                50.0000 2.8361 -74.0200 50.0000 0.0000 -82.7485 3.4412
                50.0000 -1.3802 -84.2814 50.0000 0.0000 -82.7485 1.0000
                50.0000 -1.1848 -84.8006 50.0000 0.0000 -82.7485 1.0000
                50.0000 -0.9009 -85.5211 50.0000 0.0000 -82.7485 1.0000
                50.0000 0.0000 0.0000 50.0000 -1.0000 2.0000 2.3669
                50.0000 -1.0000 2.0000 50.0000 0.0000 0.0000 2.3669
                50.0000 2.4900 -0.0010 50.0000 -2.4900 0.0009 7.1792
                50.0000 2.4900 -0.0010 50.0000 -2.4900 0.0010 7.1792
                50.0000 2.4900 -0.0010 50.0000 -2.4900 0.0011 7.2195
                50.0000 2.4900 -0.0010 50.0000 -2.4900 0.0012 7.2195
                50.0000 -0.0010 2.4900 50.0000 0.0009 -2.4900 4.8045
                50.0000 -0.0010 2.4900 50.0000 0.0010 -2.4900 4.8045
                50.0000 -0.0010 2.4900 50.0000 0.0011 -2.4900 4.7461
                50.0000 2.5000 0.0000 50.0000 0.0000 -2.5000 4.3065
                50.0000 2.5000 0.0000 73.0000 25.0000 -18.0000 27.1492
                50.0000 2.5000 0.0000 61.0000 -5.0000 29.0000 22.8977
                50.0000 2.5000 0.0000 56.0000 -27.0000 -3.0000 31.9030
                50.0000 2.5000 0.0000 58.0000 24.0000 15.0000 19.4535
                50.0000 2.5000 0.0000 50.0000 3.1736 0.5854 1.0000
                50.0000 2.5000 0.0000 50.0000 3.2972 0.0000 1.0000
                50.0000 2.5000 0.0000 50.0000 1.8634 0.5757 1.0000
                50.0000 2.5000 0.0000 50.0000 3.2592 0.3350 1.0000
                60.2574 -34.0099 36.2677 60.4626 -34.1751 39.4387 1.2644
                63.0109 -31.0961 -5.8663 62.8187 -29.7946 -4.0864 1.2630
                61.2901 3.7196 -5.3901 61.4292 2.2480 -4.9620 1.8731
                35.0831 -44.1164 3.7933 35.0232 -40.0716 1.5901 1.8645
                22.7233 20.0904 -46.6940 23.0331 14.9730 -42.5619 2.0373
                36.4612 47.8580 18.3852 36.2715 50.5065 21.2231 1.4146
                90.8027 -2.0831 1.4410 91.1528 -1.6435 0.0447 1.4441
                90.9257 -0.5406 -0.9208 88.6381 -0.8985 -0.7239 1.5381
                6.7747 -0.2908 -2.4247 5.8714 -0.0985 -2.2286 0.6377
                2.0776 0.0795 -1.1350 0.9033 -0.0636 -0.5514 0.9082
                """;
        int pair = 0;
        for (String line : data.strip().split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            double[] values = new double[7];
            for (int i = 0; i < values.length; i++) values[i] = Double.parseDouble(parts[i]);
            LabColor first = new LabColor(values[0], values[1], values[2]);
            LabColor second = new LabColor(values[3], values[4], values[5]);
            assertEquals(values[6], ColorDifference.deltaE00(first, second), 0.000051, "pair " + ++pair);
        }
        assertEquals(34, pair);
    }

    @Test
    void scoresWithRawPrecisionAndRoundsOnlyForDisplay() {
        ColorScorer scorer = new ColorScorer(ColorGameConfig.initial());
        ColorScore exact = scorer.score("#3A7FD5", "#3a7fd5");
        assertEquals(0, exact.colorDistance(), 1e-12);
        assertEquals(10, exact.score(), 1e-12);
        assertEquals(new BigDecimal("10.00"), exact.displayScore());

        ColorScore close = scorer.score("#3A7FD5", "#4285D0");
        ColorScore far = scorer.score("#3A7FD5", "#FF0000");
        assertTrue(close.colorDistance() > 0 && close.colorDistance() < far.colorDistance());
        assertTrue(close.score() < 10 && close.score() > far.score());
        assertTrue(far.score() >= 0);
        assertTrue(close.score() >= 0 && close.score() <= 10);
        assertThrows(IllegalArgumentException.class, () -> scorer.score("#3A7FD5", "red"));
    }

    @Test
    void generatesValidRgbTargetsAndRejectsInvalidScale() {
        ColorGenerator generator = new ColorGenerator();
        for (int i = 0; i < 100; i++) {
            String hex = generator.next().toHex();
            assertEquals(7, hex.length());
            assertEquals(hex, SrgbColor.parse(hex).toHex());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ColorGameConfig(new GameConfig("test-v1", 5, 3000, 750, 10000, 3000), Double.NaN));
    }
}
