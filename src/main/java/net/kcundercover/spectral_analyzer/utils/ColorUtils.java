package net.kcundercover.spectral_analyzer.utils;

import javafx.scene.paint.Color;

public class ColorUtils {

    /**
     * Converts a JavaFX Color object into a standard 8-character Hex String (#RRGGBBAA).
     */
    public static String toHexString(Color color) {
        return String.format("#%02X%02X%02X%02X",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255),
                (int)(color.getOpacity() * 255));
    }

    /**
     * Converts an 8-character Hex String back into a functioning JavaFX Color object.
     */
    public static Color fromHexString(String hex) {
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.YELLOW; // Smart fallback if file is corrupted
        }
    }
}
