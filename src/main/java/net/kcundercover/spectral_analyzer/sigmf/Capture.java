package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record Capture(
    @JsonProperty("core:sample_start") long sampleStart,
    @JsonProperty("core:frequency") double frequency
) {}
