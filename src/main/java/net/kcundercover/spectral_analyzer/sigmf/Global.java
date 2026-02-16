package net.kcundercover.spectral_analyzer.sigmf;

import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Set;

/**
 * The global field of the SigMF meta data
* @param datatype Data type string
* @param sampleRate Sample rate
* @param version SigMF version used
* @param extensions Extension for custom fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Global(
    @JsonProperty("core:datatype") String datatype,
    @JsonProperty("core:sample_rate") Double sampleRate,
    @JsonProperty("core:version") String version,

    // The "Catch-all" for everything else
    @JsonAnySetter
    @JsonAnyGetter
    Map<String, Object> extensions) {

    /**
     * Constructor
     * @param datatype Data type string
     * @param sampleRate Sample rate
     * @param version SigMF version used
     * @param extensions Extension for custom fields.
     */
    public Global {
        // Validation/Defaults
        if (sampleRate == null) {
            sampleRate = Double.valueOf(1000000.0);
        }
        if (version == null) {
            version = "1.0.0";
        }

        extensions = (extensions == null) ? Map.of() : Map.copyOf(extensions);
    }

    /** Supported data types */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "cf32_le", "cf32_be", "ci16_le", "ci16_be");

    /**
     * Getter for extensions fields
     * @return The custom fields
     */
    @JsonAnyGetter
    public Map<String, Object> extensions() {
        return Map.copyOf(extensions);
    }

    /**
     * Calculates bytes per I/Q pair based on SigMF convention
     * @return The number of bytes per sample
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
