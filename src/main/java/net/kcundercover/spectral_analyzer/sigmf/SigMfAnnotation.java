package net.kcundercover.spectral_analyzer.sigmf;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

// @JsonIgnoreProperties(ignoreUnknown = true)

public class SigMfAnnotation {
    @JsonProperty("core:sample_start") private long sampleStart;
    @JsonProperty("core:sample_count") private long sampleCount;
    @JsonProperty("core:freq_lower_edge") private Double freqLowerEdge;
    @JsonProperty("core:freq_upper_edge") private Double freqUpperEdge;
    @JsonProperty("core:label") private String label;
    @JsonProperty("core:comment") private String comment;

    // Default constructor for Jackson
    public SigMfAnnotation() {}

    /**
     * Constructor to create a copy from input annot
     * @param annot Source of annotation to clone.
     */
    public SigMfAnnotation(SigMfAnnotation annot) {
        this.sampleStart = annot.sampleStart;
        this.sampleCount = annot.sampleCount;
        this.freqLowerEdge = annot.freqLowerEdge;
        this.freqUpperEdge = annot.freqUpperEdge;
        this.label = annot.label;
        this.comment = annot.comment;
    }

    /**
     * Create a clone of the
     * @param annot Source of annotation to clone.
     */
    public void copy(SigMfAnnotation annot) {
        this.sampleStart = annot.sampleStart;
        this.sampleCount = annot.sampleCount;
        this.freqLowerEdge = annot.freqLowerEdge;
        this.freqUpperEdge = annot.freqUpperEdge;
        this.label = annot.label;
        this.comment = annot.comment;
    }

    /**
     * Constructor with every parameter
     * @param sampleStart Sample start
     * @param sampleCount Sample count
     * @param freqLowerEdge Frequency of lower edge
     * @param freqUpperEdge frequency of upper edge
     * @param label Label of the annotation
     * @param comment Comment of the annotation
     */
    public SigMfAnnotation(long sampleStart, long sampleCount, Double freqLowerEdge,
                           Double freqUpperEdge, String label, String comment) {
        this.sampleStart = sampleStart;
        this.sampleCount = sampleCount;
        this.freqLowerEdge = freqLowerEdge;
        this.freqUpperEdge = freqUpperEdge;
        this.label = label;
        this.comment = comment;
    }

    // --------------------------------------------------------
    // Support custom fields
    // --------------------------------------------------------
    private Map<String, Object> customFields = new HashMap<>();

    @JsonAnySetter
    public void addCustomField(String name, Object value) {
        customFields.put(name, value);
    }

    // Writes them back out during serialization
    @JsonAnyGetter
    public Map<String, Object> getCustomFields() {
        // Fix EI: Wrap the internal map so it cannot be modified externally
        return Map.copyOf(customFields);
    }

    // --------------------------------------------------------
    // Getters
    // --------------------------------------------------------
    public long getSampleStart() {
        return sampleStart;
    }
    public long getSampleCount() {
        return sampleCount;
        }
    public String getLabel() {
        return label;
    }
    public String getComment() {
        return comment;
    }
    public Double getFreqLowerEdge() {
        return freqLowerEdge;
    }
    public Double getFreqUpperEdge() {
        return freqUpperEdge;
    }

    // Setters (These fix your compilation errors)
    public void setLabel(String label) {
         this.label = label;
        }
    public void setComment(String comment) {
        this.comment = comment;
    }
    public void setFreqLowerEdge(Double freq) {
        this.freqLowerEdge = freq;
    }
    public void setFreqUpperEdge(Double freq) {
        this.freqUpperEdge = freq;
    }
    public void setSampleStart(Long start) {
        this.sampleStart = start;
    }
    public void setSampleCount(Long count) {
        this.sampleCount = count;
    }
    public long sampleStart() {
        return this.sampleStart;
    }
}
