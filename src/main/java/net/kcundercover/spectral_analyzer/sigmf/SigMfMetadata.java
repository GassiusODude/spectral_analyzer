package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record SigMfMetadata(
    @JsonProperty("global") Global global,
    @JsonProperty("captures") List<Capture> captures,
    @JsonProperty("annotations") List<Map<String, Object>> annotations
) {}
