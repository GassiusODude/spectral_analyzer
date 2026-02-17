package net.kcundercover.spectral_analyzer.services;

// import org.springframework.beans.factory.annotation.Autowired;
import java.nio.MappedByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service to call the extract and down convert service asynchronously
 */
@Service
public class AsyncExtractDownConvertService {

    /**
     * Default Constructor
     */
    public AsyncExtractDownConvertService(){}

    @Autowired
    private ExtractDownConvertService syncService;

    // Use a dedicated thread pool so DSP doesn't starve the UI or Web server
    private final ExecutorService dspExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            runnable -> {
                Thread t = new Thread(runnable);
                t.setName("DSP-Worker");
                t.setDaemon(true); // Ensures threads don't block app shutdown
                return t;
            }
    );

    /**
     * Extract the samples, shift in frequency and down convert.
     * @param buffer The data buffer
     * @param startSample The sample offset from start of buffer
     * @param count Count in samples to extract
     * @param datatype The data type of the buffer
     * @param freqOff Frequency offset to apply
     * @param down Down convert rate
     * @param fast Use fast mode or not.  Fast mode has less out of bound attenuation
     * @return The completable future output
     */
    public CompletableFuture<double[][]> extractAndDownConvertAsync(
            MappedByteBuffer buffer, long startSample,
            int count, String datatype, double freqOff, int down, boolean fast) {

        return CompletableFuture.supplyAsync(() ->
            syncService.extractAndDownConvert(buffer, startSample, count, datatype, freqOff, down, fast),
            dspExecutor
        );
    }
}
