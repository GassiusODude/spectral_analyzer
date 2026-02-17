package net.kcundercover.spectral_analyzer.sigmf;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The capture field of the SigMF Metadata
* @param sampleStart The start sample of the capture
* @param frequency Frequency of the capture
* @param datetime The timestamp of the capture
* @param extensions Extension fields
*/
@JsonIgnoreProperties(ignoreUnknown = true)
public record Capture(
    @JsonProperty("core:sample_start") Long sampleStart,
    @JsonProperty("core:frequency") Double frequency,
    @JsonProperty("core:datetime") String datetime,
    @JsonAnySetter
    @JsonAnyGetter
    Map<String, Object> extensions
) {
    /**
     * Constructor
     * @param sampleStart The start sample of the capture
     * @param frequency Frequency of the capture
     * @param datetime The timestamp of the capture
     * @param extensions Extension fields
     */
    public Capture {
        // Validation/Defaults
        if (sampleStart == null ) {
            sampleStart = Long.valueOf(0L);
        }
        if (frequency == null) {
            frequency = Double.valueOf(0.0);
        }
        // Ensure map is initialized to avoid NullPointerExceptions
        extensions = (extensions == null) ? Map.of() : Map.copyOf(extensions);
    }

    /**
     * Getter for custom fields
     * @return a copy of the extension fields
     */
    @JsonAnyGetter
    public Map<String, Object> extensions() {
        // Fix EI: Prevent internal representation exposure
        return Map.copyOf(extensions);
    }
    /**
     * Logic to check if this capture has a valid timestamp
     * @return True if capture has a valid timestamp.
     */
    public boolean hasTimestamp() {
        return datetime != null && !datetime.isBlank();
    }
}
