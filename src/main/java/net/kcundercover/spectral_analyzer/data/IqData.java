package net.kcundercover.spectral_analyzer.data;


import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
import net.kcundercover.spectral_analyzer.sigmf.SigMfMetadata;
import net.kcundercover.spectral_analyzer.sigmf.Capture;
import net.kcundercover.spectral_analyzer.sigmf.Global;

/**
 * A container for IQ data and associated SigMF Meta data
 */
public class IqData {
    private static final Logger IQ_LOGGER = LoggerFactory.getLogger(IqData.class);
    private String name;
    private double[][] iqSamples;
    private double sampleRate;
    private SigMfMetadata meta;
    private double bandwidth;

    /**
     * Constructor for new IQ Data
     *
     * Set up an object
     *
     * @param name A name for the IQ Data
     * @param iq The downsampled iq data
     * @param newFs New sample rate
     * @param origMeta the original SigMF metadata
     * @param origAnnot THe annotation from the original file.
     */
    public IqData(String name, double[][] iq, double newFs, SigMfMetadata origMeta, SigMfAnnotation origAnnot) {
        this.name = name;
        this.sampleRate = newFs;

        // Fix EI2: Perform a deep defensive copy
        if (iq == null) {
            this.iqSamples = new double[0][0];
        } else {
            this.iqSamples = new double[iq.length][];
            for (int i = 0; i < iq.length; i++) {
                this.iqSamples[i] = iq[i].clone(); // Deep copy of the inner array
            }
        }

        // --------------------  prepare data for new SigMF Metadata  -----------------------------
        double newFc = 0.5 * (origAnnot.getFreqLowerEdge() + origAnnot.getFreqUpperEdge());
        this.bandwidth = origAnnot.getFreqUpperEdge() - origAnnot.getFreqLowerEdge();

        String newTimeStamp = null;
        if (origMeta.captures().get(0).hasTimestamp()) {
            newTimeStamp = getNewTimestamp(
                origMeta.captures().get(0).datetime(),
                origAnnot.getSampleStart(), origMeta.global().sampleRate());
        }

        // ----------------------  create capture, global, metadata  ------------------------------
        // NOTE: these are immutable records...prepare all components to pass to constructor
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
     * @param origIsoString The original date time string from SigMF Capture
     * @param sampleStart Sample start in original sample space, used to update the date time
     * @param sampleRate Original sample rate
     * @return The updated date time string.
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

    /**
     * Helper method to display information contained by this object
     */
    public void display() {
        IQ_LOGGER.info("Name = {}, Sample Rate = {}, ", name, sampleRate);
        IQ_LOGGER.info("Num Samples = {}", iqSamples[0].length);

        Capture capture = meta.captures().get(0);
        IQ_LOGGER.info("Capture = {}", capture.toString());
    }

    /**
     * Access the IqSamples (but a safe copy)
     * @return The copy of the IQ data
     */
    public double[][] getIqSamples() {
        if (this.iqSamples == null) {
            return null;
        }
        // Create a new outer array
        double[][] copy = new double[this.iqSamples.length][];
        for (int i = 0; i < this.iqSamples.length; i++) {
            // Create a new inner array for each row
            if (this.iqSamples[i] != null) {
                copy[i] = this.iqSamples[i].clone();
            }
        }
        return copy;
    }

    /**
     * Get a Map to represent some properties calculated from data and metadata
     * @return Some key properties
     */
    public Map<String, Object> getData() {
        Map<String, Object> dataContainer = new HashMap<>();
        dataContainer.put("iqSamples", getIqSamples());
        double sampleRate = meta.global().sampleRate();
        dataContainer.put("sampleRate", sampleRate);
        dataContainer.put("centerFrequency", meta.captures().get(0).frequency());
        long numSamples = (long) this.iqSamples[0].length;
        dataContainer.put("duration", ((double) numSamples) / sampleRate);
        dataContainer.put("bandwidth", this.bandwidth);

        return dataContainer;
    }
}
