package net.kcundercover.spectral_analyzer.sigmf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class SigMfHelper {
    private final ObjectMapper mapper = new ObjectMapper()
        // This prevents the UnrecognizedPropertyException
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private SigMfMetadata metadata;
    private MappedByteBuffer dataBuffer;

    public void load(Path metaPath) throws Exception {
        // 1. Load Metadata
        this.metadata = mapper.readValue(metaPath.toFile(), SigMfMetadata.class);

        // 2. Identify Data File (standard requires same base name with .sigmf-data)
        String dataFileName = metaPath.toString().replace(".sigmf-meta", ".sigmf-data");
        File dataFile = new File(dataFileName);

        // 3. Memory Map the Data File
        try (RandomAccessFile raf = new RandomAccessFile(dataFile, "r");
             FileChannel channel = raf.getChannel()) {

            this.dataBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

            // Set Endianness based on SigMF datatype (e.g., cf32_le)
            if (this.metadata.global().datatype().endsWith("_le")) {
                dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                dataBuffer.order(ByteOrder.BIG_ENDIAN);
            }
        }
    }

    public SigMfMetadata getMetadata() { return metadata; }
    public MappedByteBuffer getDataBuffer() { return dataBuffer; }
}
