package net.kcundercover.spectral_analyzer.data;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
// import javafx.beans.property.ReadOnlyBooleanProperty;
// import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * Annotation Row to be use in displaying Table of annotations
 */
public class AnnotationRow {
    private final StringProperty label;
    private final StringProperty comment;
    private final DoubleProperty startTime; // derived from offset / sample_rate
    private final DoubleProperty duration;  // derived from sample_count / sample_rate
    private final DoubleProperty centerFreq;
    private final DoubleProperty bandwidth;
    // private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(false);
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final AnnotationGroup associatedGroup;

    /**
     * Constructor for the Annotation
     * @param label Label of the annotation
     * @param comment Description of the annotation
     * @param start Time start (in seconds)
     * @param dur Time duratio (in seconds)
     * @param cf Center frequency (Hertz)
     * @param bw Bandwidth (Hertz)
     * @param group Associated AnnotationGroup, access to SigMFAnnotation to be updated
     */
    public AnnotationRow(
            String label, String comment,
            double start, double dur, double cf, double bw,
            AnnotationGroup group) {

        this.label = new SimpleStringProperty(label);
        this.comment = new SimpleStringProperty(comment);
        this.startTime = new SimpleDoubleProperty(start);
        this.duration = new SimpleDoubleProperty(dur);
        this.centerFreq = new SimpleDoubleProperty(cf);
        this.bandwidth = new SimpleDoubleProperty(bw);
        this.associatedGroup = group;
    }

    public StringProperty labelProperty() {
        return this.label;
    }
    public void setLabel(String newString) {
        this.label.setValue(newString);
    }
    public String getLabel() {
        return label.get();
    }
    public StringProperty commentProperty() {
        return this.comment;
    }
    public String getComment() {
        return comment.get();
    }
    public void setComment(String newString) {
        this.comment.setValue(newString);
    }
    public double getStartTime() {
        return startTime.get();
    }
    public double getDuration() {
        return duration.get();
    }
    public double getCenterFreq() {
        return centerFreq.get();
    }
    public double getBandwidth() {
        return bandwidth.get();
    }

    @SuppressFBWarnings
    public BooleanProperty selectedProperty() {
        // return selected.getReadOnlyProperty();
        return selected;
    }
    // public ReadOnlyBooleanProperty selectedProperty() {
    //     return selected.getReadOnlyProperty();
    // }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
    public boolean isSelected() {
        return selected.get();
    }

    public AnnotationGroup getAssociatedGroup() {
        return associatedGroup;
    }
}
