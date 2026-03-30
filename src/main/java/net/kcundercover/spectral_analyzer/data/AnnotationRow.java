package net.kcundercover.spectral_analyzer.data;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;


public class AnnotationRow {
    private final StringProperty label;
    private final StringProperty comment;
    private final DoubleProperty startTime; // derived from offset / sample_rate
    private final DoubleProperty duration;  // derived from sample_count / sample_rate
    private final DoubleProperty centerFreq;
    private final DoubleProperty bandwidth;

    public AnnotationRow(String label, String comment, double start, double dur, double cf, double bw) {
        this.label = new SimpleStringProperty(label);
        this.comment = new SimpleStringProperty(comment);
        this.startTime = new SimpleDoubleProperty(start);
        this.duration = new SimpleDoubleProperty(dur);
        this.centerFreq = new SimpleDoubleProperty(cf);
        this.bandwidth = new SimpleDoubleProperty(bw);
    }


    public String getLabel() {
        return label.get();
    }
    public String getComment() {
        return comment.get();
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
}
