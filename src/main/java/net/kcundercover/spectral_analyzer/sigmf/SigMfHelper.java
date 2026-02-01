package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
/**
 * SigMF Helper object
 */
public class SigMfHelper {
    private static final Logger logger = LoggerFactory.getLogger(SigMfHelper.class);
    private final ObjectMapper mapper = new ObjectMapper()
        // This prevents the UnrecognizedPropertyException
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private SigMfMetadata metadata;
    private MappedByteBuffer dataBuffer;
    private Path inputMeta;

    /**
     * Loads a SigMF meta file
     *
     * @param metaPath Path to the SigMF meta file.
     * @throws Exception
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

    public File getCurrentMetaFile() {
        if (inputMeta == null) return null;

        File file = inputMeta.toFile();
        if (file.getName().endsWith(".sigmf-data")) {
            String path = file.getAbsolutePath().replace(".sigmf-data", ".sigmf-meta");
            return new File(path);
        }
        return file;
    }

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "SigMfMetadata is immutable")
    public SigMfMetadata getMetadata() {
        return metadata;
    }
    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Intentional: buffer is shared for performance")
    public MappedByteBuffer getDataBuffer() {
        return dataBuffer;
    }

    // public ByteBuffer getDataBuffer() {
    //     return dataBuffer.asReadOnlyBuffer(); // ✅
    // }
    /**
     * Get the annotations list and return the List of SigMfAnnotations
     * @return List of SigMF Annotation objects
     */
    public List<SigMfAnnotation> getParsedAnnotations() {
        ObjectMapper mapper = new ObjectMapper();
        // This converts the raw Map from Jackson into your specific SigMfAnnotation records
        return mapper.convertValue(metadata.annotations(),
            new TypeReference<List<SigMfAnnotation>>() {});
    }

    public void saveSigMF(List<SigMfAnnotation> annotationList) {
        try {
            // Use the class-level mapper and just enable the feature for this call
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            List<Map<String, Object>> annotationMaps = annotationList.stream()
                .map(annot -> mapper.convertValue(annot, new TypeReference<Map<String, Object>>() {}))
                .toList();

            this.metadata = new SigMfMetadata(
                metadata.global(),
                metadata.captures(),
                annotationMaps
            );

            mapper.writeValue(getCurrentMetaFile(), this.metadata);
            logger.info("SigMF metadata saved successfully.");
        } catch (IOException e) {
            logger.error("Failed to save SigMF file", e);
        }
    }

}
