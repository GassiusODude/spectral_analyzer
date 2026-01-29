package net.kcundercover.spectral_analyzer;

import javafx.scene.paint.Color;

/**
 * A record to track a string label with a color.  This is intended to be used for
 * custom color coding of unique labels from SigMF Annotations.
 */
public record AnnotationStyle(String label, Color color) {}
