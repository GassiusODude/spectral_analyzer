package net.kcundercover.spectral_analyzer.sigmf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Helper class for handling non-conforming datasets that lack proper SigMF metadata.
 * This class can generate a minimal SigMF metadata file based on the input data file
 * and provided parameters, allowing the rest of the application to process the data
 * as if it were a standard SigMF dataset.
 */
public class NonconformingDatasetHelper {
    private File metaFile;
    private Global global;
    private Capture capture;
    private SigMfMetadata meta;

    /**
     * Constructor for NonconformingDatasetHelper
     * @param file1 Raw data file without proper SigMF metadata
     * @param sampleRate Sample rate of the data in Hz
     * @param centerFreq Center frequency of the data in Hz
     * @param dataType Data type string for SigMF metadata (e.g. "cf32_le", "ci16_le", etc.)
     */
    public NonconformingDatasetHelper(File file1, double sampleRate, double centerFreq, String dataType) {
        // This is a helper for non-conforming datasets that don't have proper SigMF metadata.
        // It creates a minimal SigMF metadata object based on the file name and provided parameters.

        String timestamp = getCaptureTimestamp(file1);
        String datasetName = file1.getName();

        this.metaFile = buildMetaFile(file1);

        this.global = new Global(
            dataType,
            sampleRate,
            "1.0.0",
            datasetName,
            Map.of());

        this.capture = new Capture(
            Long.valueOf(0L),   // samplestart
            Double.valueOf(centerFreq), timestamp, Map.of());

        this.meta = new SigMfMetadata(
            global,
            List.of(capture),
            List.of());
    }

    /**
     * Get the absolute path to the meta file that will be generated for this non-conforming dataset.
     * @return The target path for the generated SigMF metadata file.
     */
    public String getMetaFilePath() {
        return metaFile.getAbsolutePath();
    }

    /**
     * Access the data file las modified time.
     * @param file Data file to analyze
     * @return The timestamp of the file.
     */
    public static String getCaptureTimestamp(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            // Default to creation time, fallback to last modified
            Instant instant = attrs.creationTime().toInstant();
            if (instant.equals(Instant.EPOCH)) {
                instant = attrs.lastModifiedTime().toInstant();
            }
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        } catch (IOException e) {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.now()); // Fallback to current time
        }
    }

    /**
     * Guess the data type base on the file extension
     * Defaults to cf32_le if no known extension is found, which is a common format for SDR captures and a sensible engineering default.
     * @param filename Data file name to analyze
     * @return Guess at the datatype based on the file extension, or "cf32_le" if unknown
     */
    public static String guessDatatypeFromExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".cs16") || lower.endsWith(".ci16")) {
            return "ci16_le";
        }  else if (lower.endsWith(".cf32")) {
            return "cf32_le";
        } else if (lower.endsWith(".cf64")) {
            return "cf64_le";
        } else if (lower.endsWith(".ci8")) {
            return "ci8";
        } else if (lower.endsWith(".cu8")) {
            return "cu8";
        }
        return "cf32_le"; // Sensible engineering default
    }

    /**
     * Build the SigMF meta file based on the input file's base name,
     * replacing the extension with .sigmf-meta
     *
     * @param file Input data file (expecting non sigmf-data extension)
     * @return The sigmf-meta file with same base name as the data file
     */
    private static File buildMetaFile(File file) {
        String fileName = file.getName();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
        File parent = file.getParentFile();
        return new File(parent, baseName + ".sigmf-meta");
    }

    /**
     * Writes the SigMF metadata to the .sigmf-meta file specified by `metaFile`.
     */
    public void writeSigMfFile() {
        try {
            File parentDir = metaFile.getParentFile();

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(metaFile, meta);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write SigMF metadata file: " + metaFile, e);
        }
    }

}
