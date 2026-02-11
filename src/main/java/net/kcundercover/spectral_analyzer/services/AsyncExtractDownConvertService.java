package net.kcundercover.spectral_analyzer.services;

// import org.springframework.beans.factory.annotation.Autowired;
import java.nio.MappedByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AsyncExtractDownConvertService {

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

    public CompletableFuture<double[][]> extractAndDownConvertAsync(
            MappedByteBuffer buffer, long startSample,
            int count, String datatype, double freqOff, int down, boolean fast) {

        return CompletableFuture.supplyAsync(() ->
            syncService.extractAndDownConvert(buffer, startSample, count, datatype, freqOff, down, fast),
            dspExecutor
        );
    }
}
