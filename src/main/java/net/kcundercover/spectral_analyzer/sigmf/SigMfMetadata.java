package net.kcundercover.spectral_analyzer.sigmf;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The SigMF Meta data.  Holds global, captures and annotations
 * @param global Global object
 * @param captures Captures list
 * @param annotations List of annotations1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SigMfMetadata(
    @JsonProperty("global") Global global,
    @JsonProperty("captures") List<Capture> captures,
    @JsonProperty("annotations") List<SigMfAnnotation> annotations // Changed from List<Map>
) {

    /**
     * The default constructor
     * @param global Global attribute (sample rate, author, description)
     * @param captures Capture attributes (center frequency, time, sample offset)
     * @param annotations Annotations
     */
    public SigMfMetadata {
        // Handle potential nulls and create immutable defensive copies
        captures = (captures == null) ? List.of() : List.copyOf(captures);
        annotations = (annotations == null) ? List.of() : List.copyOf(annotations);
    }

    /**
     * Getter for captures
     * @return Returns a copy of the list of captures
     */
    public List<Capture> captures() {
        return List.copyOf(captures); // defensive copy, immutable
    }

    /**
     * Getter for annotations.
     * @return A copy of the annotations list
     */
    public List<SigMfAnnotation> annotations() {
        // Fix EI: Return an immutable list copy
        return List.copyOf(annotations);
    }
}
