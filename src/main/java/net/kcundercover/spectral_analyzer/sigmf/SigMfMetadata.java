package net.kcundercover.spectral_analyzer.sigmf;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SigMfMetadata(
    @JsonProperty("global") Global global,
    @JsonProperty("captures") List<Capture> captures,
    @JsonProperty("annotations") List<SigMfAnnotation> annotations // Changed from List<Map>
) {

    public SigMfMetadata {
        // Handle potential nulls and create immutable defensive copies
        captures = (captures == null) ? List.of() : List.copyOf(captures);
        annotations = (annotations == null) ? List.of() : List.copyOf(annotations);
    }

    public List<Capture> captures() {
        return List.copyOf(captures); // defensive copy, immutable
    }

    public List<SigMfAnnotation> annotations() {
        // Fix EI: Return an immutable list copy
        return List.copyOf(annotations);
    }
}
