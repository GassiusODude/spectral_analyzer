package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record SigMfMetadata(
    @JsonProperty("global") Global global,
    @JsonProperty("captures") List<Capture> captures,
    @JsonProperty("annotations") List<Map<String, Object>> annotations
) {

    public SigMfMetadata {
        // Defensive copy: makes immutable copies of lists
        captures = List.copyOf(captures); // immutable copy of list
        // annotations = annotations.stream()
        //                          .map(Map::copyOf) // shallow copy each map
        //                          .toList();        // immutable list
        annotations = new ArrayList<>(annotations); // shallow copy, mutable

    }

    public List<Capture> captures() {
        return List.copyOf(captures); // defensive copy, immutable
    }

    public List<Map<String, Object>> annotations() {
        return List.copyOf(annotations); // defensive copy
    }
}
