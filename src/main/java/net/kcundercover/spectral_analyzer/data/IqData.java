package net.kcundercover.spectral_analyzer.data;


import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
import net.kcundercover.spectral_analyzer.sigmf.SigMfMetadata;
import net.kcundercover.spectral_analyzer.sigmf.Capture;
import net.kcundercover.spectral_analyzer.sigmf.Global;

public class IqData {
    private String name;
    private double[][] iqSamples;
    private double sampleRate;
    private SigMfMetadata meta;
    private SigMfAnnotation annotation;

    /**
     * Constructor for new IQ Data
     *
     * Set up an object
     *
     * @param iq The downsampled iq data
     * @param newFs New sample rate
     * @param origMeta the original SigMF metadata
     * @param origAnnot THe annotation from the original file.
     */
    public IqData(String name, double[][] iq, double newFs, SigMfMetadata origMeta, SigMfAnnotation origAnnot) {
        this.name = name;
        this.iqSamples = iq;
        this.sampleRate = newFs;

        double newFc = 0.5 * (origAnnot.getFreqLowerEdge() + origAnnot.getFreqUpperEdge());

        String newTimeStamp = null;
        if (origMeta.captures().get(0).hasTimestamp()) {
            newTimeStamp = getNewTimestamp(
                origMeta.captures().get(0).datetime(),
                origAnnot.getSampleStart(), origMeta.global().sampleRate());
        }

        Capture newCapture = new Capture(0L, newFc, newTimeStamp, Map.of());
        Global newGlobal = new Global(
            "cf64_le",
            newFs,
            origMeta.global().version(),
            Map.of());

        this.meta = new SigMfMetadata(
            newGlobal, List.of(newCapture), List.of());
    }

    /**
     * Get updated timesamp based on original SigMFMetadata and the sampleStart of the provided annotation.
     */
    public static String getNewTimestamp(String origIsoString, long sampleStart, double sampleRate) {
        // Load: Parse the ISO 8601 string (e.g., "2023-10-01T12:00:00Z")
        Instant originalInstant = Instant.parse(origIsoString);

        // Calculate offset in seconds and add to the instant
        // Note: Using nanoseconds for maximum precision
        double offsetSeconds = sampleStart / sampleRate;
        long offsetNanos = (long) (offsetSeconds * 1_000_000_000L);

        Instant newInstant = originalInstant.plusNanos(offsetNanos);

        // Format: Convert back to a SigMF-compliant string
        return newInstant.toString();
    }
}