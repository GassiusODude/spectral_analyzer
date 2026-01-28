package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SigMfAnnotation(
    @JsonProperty("core:sample_start") long sampleStart,
    @JsonProperty("core:sample_count") long sampleCount,
    @JsonProperty("core:freq_lower_edge") Double freqLowerEdge,
    @JsonProperty("core:freq_upper_edge") Double freqUpperEdge,
    @JsonProperty("core:label") String label,
    @JsonProperty("core:comment") String comment
) {
    // Helper to check if a specific frequency is inside this annotation
    public boolean containsFrequency(double freq) {
        return freq >= freqLowerEdge && freq <= freqUpperEdge;
    }
}
