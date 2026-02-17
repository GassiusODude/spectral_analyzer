package net.kcundercover.spectral_analyzer.data;

import javafx.scene.paint.Color;

/**
 * A record to track a string label with a color.  This is intended to be used for
 * custom color coding of unique labels from SigMF Annotations.
 * @param label The label to apply this color scheme
 * @param color The color to apply on this unique label
 */
public record AnnotationStyle(String label, Color color) {}
