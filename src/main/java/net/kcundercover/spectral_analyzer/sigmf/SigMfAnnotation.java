package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SigMfAnnotation {
    @JsonProperty("core:sample_start") private long sampleStart;
    @JsonProperty("core:sample_count") private long sampleCount;
    @JsonProperty("core:freq_lower_edge") private Double freqLowerEdge;
    @JsonProperty("core:freq_upper_edge") private Double freqUpperEdge;
    @JsonProperty("core:label") private String label;
    @JsonProperty("core:comment") private String comment;

    // Default constructor for Jackson
    public SigMfAnnotation() {}

        // All-args constructor for MainController (Fixes error #1)
    public SigMfAnnotation(long sampleStart, long sampleCount, Double freqLowerEdge,
                           Double freqUpperEdge, String label, String comment) {
        this.sampleStart = sampleStart;
        this.sampleCount = sampleCount;
        this.freqLowerEdge = freqLowerEdge;
        this.freqUpperEdge = freqUpperEdge;
        this.label = label;
        this.comment = comment;
    }

    // Getters
    public long getSampleStart() { return sampleStart; }
    public long getSampleCount() { return sampleCount; }
    public String getLabel() { return label; }
    public String getComment() { return comment; }
    public Double getFreqLowerEdge() { return freqLowerEdge; }
    public Double getFreqUpperEdge() { return freqUpperEdge; }

    // Setters (These fix your compilation errors)
    public void setLabel(String label) { this.label = label; }
    public void setComment(String comment) { this.comment = comment; }
    public void setFreqLowerEdge(Double freq) { this.freqLowerEdge = freq; }
    public void setFreqUpperEdge(Double freq) { this.freqUpperEdge = freq; }

    // In SigMfAnnotation.java, make sure this exists:
    public long sampleStart() {
        return this.sampleStart;
    }
}