package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record Global(
    @JsonProperty("core:datatype") String datatype,
    @JsonProperty("core:sample_rate") Double sampleRate,
    @JsonProperty("core:version") String version
) {

    // Set of supported types for 2026
    private static final Set<String> SUPPORTED_TYPES = Set.of("cf32_le", "cf32_be", "ci16_le", "ci16_be");


    public Global {
        // If sample_rate is missing, default to 1 MHz
        if (sampleRate == null) {
            sampleRate = 1000000.0;
        }
        // If version is missing, default to v1.0.0
        if (version == null) {
            version = "1.0.0";
        }
    }

    /**
     * Calculates bytes per I/Q pair based on SigMF convention
     */
    public int getBytesPerSample() {
        if (datatype.startsWith("cf32")) {
            return 8; // 4 bytes I + 4 bytes Q
        } else if (datatype.startsWith("ci16")) {
            return 4; // 2 bytes I + 2 bytes Q
        }
        return 8; // Fallback
    }
}
