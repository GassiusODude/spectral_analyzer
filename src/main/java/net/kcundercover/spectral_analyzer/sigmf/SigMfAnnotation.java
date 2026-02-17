package net.kcundercover.spectral_analyzer.sigmf;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;


/**
 * The SigMF Annotation
 */
public class SigMfAnnotation {
    @JsonProperty("core:sample_start") private long sampleStart;
    @JsonProperty("core:sample_count") private long sampleCount;
    @JsonProperty("core:freq_lower_edge") private Double freqLowerEdge;
    @JsonProperty("core:freq_upper_edge") private Double freqUpperEdge;
    @JsonProperty("core:label") private String label;
    @JsonProperty("core:comment") private String comment;

    /** Default constructor */
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
    /**
     * Support to capturing custom fields
     */
    private Map<String, Object> customFields = new HashMap<>();

    /**
     * The support for setters on custom fields
     * @param name Name of the field
     * @param value The value of the custom field
     */
    @JsonAnySetter
    public void addCustomField(String name, Object value) {
        customFields.put(name, value);
    }

    /**
     * Support getters for all custom fields
     * @return Return a copy of the custom field
     */
    @JsonAnyGetter
    public Map<String, Object> getCustomFields() {
        // Fix EI: Wrap the internal map so it cannot be modified externally
        return Map.copyOf(customFields);
    }

    // --------------------------------------------------------
    // Getters
    // --------------------------------------------------------
    /**
     * Get the sample start of the annotation
     * @return Sample start
     */
    public long getSampleStart() {
        return sampleStart;
    }

    /**
     * Get the sample count
     * @return The sample count
     */
    public long getSampleCount() {
        return sampleCount;
    }

    /**
     * Get the label of the annotation
     * @return Label of the annotation
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get the comment of the annotation
     * @return Comment of the annotation
     */
    public String getComment() {
        return comment;
    }

    /**
     * Get the lower frequency edge
     * @return The lower frequency edge
     */
    public Double getFreqLowerEdge() {
        return freqLowerEdge;
    }

    /**
     * Get the upper frequency edge
     * @return The upper frequency edge
     */
    public Double getFreqUpperEdge() {
        return freqUpperEdge;
    }

    /**
     * Set the label
     * @param label Update the label of the annotation
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Set the comment
     * @param comment The updated comment
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
    /**
     * Set the lower frequency edge
     * @param freq The new frequency edge
     */
    public void setFreqLowerEdge(Double freq) {
        this.freqLowerEdge = freq;
    }
    /**
     * set the new upper frequency edge
     * @param freq Upper frequency edge
     */
    public void setFreqUpperEdge(Double freq) {
        this.freqUpperEdge = freq;
    }

    /**
     * Set the new sample start
     * @param start New sample start
     */
    public void setSampleStart(Long start) {
        this.sampleStart = start;
    }

    /**
     * Set the new sample count
     * @param count New sample count
     */
    public void setSampleCount(Long count) {
        this.sampleCount = count;
    }

    /**
     * Get the sample start
     * @return sample start
     */
    public long sampleStart() {
        return this.sampleStart;
    }
}
