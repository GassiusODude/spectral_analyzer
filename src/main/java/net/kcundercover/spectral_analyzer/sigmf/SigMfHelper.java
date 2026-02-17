package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SigMF Helper object
 */
public class SigMfHelper {
    private static final Logger SMH_LOGGER = LoggerFactory.getLogger(SigMfHelper.class);
    private final ObjectMapper mapper = new ObjectMapper()
        // This prevents the UnrecognizedPropertyException
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private SigMfMetadata metadata;
    private MappedByteBuffer dataBuffer;
    private Path inputMeta;

    /**
     * Default constructor
     */
    public SigMfHelper() {

    }
    /**
     * Loads a SigMF meta file
     *
     * @param metaPath Path to the SigMF meta file.
     * @throws Exception I/O or Json parsing exception
     */
    public void load(Path metaPath) throws Exception {
        // Load Metadata
        this.metadata = mapper.readValue(metaPath.toFile(), SigMfMetadata.class);

        // Identify Data File (standard requires same base name with .sigmf-data)
        String dataFileName = metaPath.toString().replace(".sigmf-meta", ".sigmf-data");
        File dataFile = new File(dataFileName);

        // Memory Map the Data File
        try (RandomAccessFile raf = new RandomAccessFile(dataFile, "r");
             FileChannel channel = raf.getChannel()) {

            this.dataBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

            // Set Endianness based on SigMF datatype (e.g., cf32_le)
            if (this.metadata.global().datatype().endsWith("_le")) {
                dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                dataBuffer.order(ByteOrder.BIG_ENDIAN);
            }
            inputMeta = metaPath;
        }
    }

    /**
     * Get the file specified by property `inputMeta`,
     * initialized in the `load()` method.
     *
     * @return File to the input metafila
     */
    public File getCurrentMetaFile() {
        if (inputMeta == null) {
            return null;
        }

        // prepare the File for the provided path
        File file = inputMeta.toFile();
        if (file.getName().endsWith(".sigmf-data")) {
            String path = file.getAbsolutePath().replace(".sigmf-data", ".sigmf-meta");
            return new File(path);
        }
        return file;
    }

    /**
     * Getter for the metadata
     * @return The metadata
     */
    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "SigMfMetadata is immutable")
    public SigMfMetadata getMetadata() {
        return metadata;
    }

    /**
     * Getter for the data buffer
     * @return the data buffer
     */
    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Intentional: buffer is shared for performance")
    public MappedByteBuffer getDataBuffer() {
        return dataBuffer;
    }

    /**
     * Get the annotations list and return the List of SigMfAnnotations
     * @return List of SigMF Annotation objects
     */
    public List<SigMfAnnotation> getParsedAnnotations() {
        return metadata.annotations();
    }

    /**
     * Save the SigMF file with the updated annotationList
     * @param annotationList List of annotations.
     */
    public void saveSigMF(List<SigMfAnnotation> annotationList) {
        try {
            // Use the class-level mapper and just enable the feature for this call
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            this.metadata = new SigMfMetadata(
                metadata.global(),
                metadata.captures(),
                annotationList
            );

            mapper.writeValue(getCurrentMetaFile(), this.metadata);
            SMH_LOGGER.info("SigMF metadata saved successfully.");
        } catch (IOException e) {
            SMH_LOGGER.error("Failed to save SigMF file", e);
        }
    }

}
