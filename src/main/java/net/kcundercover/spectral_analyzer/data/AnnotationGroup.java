package net.kcundercover.spectral_analyzer.data;

import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.scene.control.Tooltip;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;

/**
 * AnnotationGroup is used to track the UI {@code label} and {@code rect})
 * {@code tooltip} and the SigMFAnnotation {@code data}.
 *
 * {@code label} is used to show the type of annotation
 * {@code rect} is a overlaying Rectangle to visually show time/frequency position
 * {@code data} is the SigMF Annotation information.
 * {@code tooltip} is the comment from the annotation shown as a tooltip for the rectangle.
 */
public class AnnotationGroup {
    public Rectangle rect;
    public Label label;
    public SigMfAnnotation data;
    public Tooltip tooltip;
}
