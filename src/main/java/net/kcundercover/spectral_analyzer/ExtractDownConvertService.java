package net.kcundercover.spectral_analyzer;

import org.apache.commons.math3.complex.Complex;
import org.springframework.stereotype.Service;
import java.nio.MappedByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.jdsp.signal.Resampler;

/**
 * A service to calculate downconvert a signal segment
 *
 * This class support shifting a signal segment in frequency and then
 * downsampling.  The downsampled signal is smaller and requires less
 * processing to analyze.
 */
@Service
public class ExtractDownConvertService {
    private static final Logger logger = LoggerFactory.getLogger(ExtractDownConvertService.class);

    public double[][] extractAndDownConvert(
            MappedByteBuffer buffer, long startSample,
            int count, String datatype, double freqOff, int down) {
        // Default to use the fast mode
        return extractAndDownConvert(
            buffer, startSample, count, datatype, freqOff, down, false);
    }


    /**
     * Extracts IQ samples and performs a simple down-conversion/resample.
     */
    public double[][] extractAndDownConvert(
            MappedByteBuffer buffer, long startSample,
            int count, String datatype, double freqOff, int down,
            boolean fast) {

        logger.info("Extracting {} samples, Down-converting by factor: {}", count, down);

        // 1. Determine bytes per IQ pair
        int bytesPerIQ = datatype.startsWith("ci16") ? 4 : 8;
        long startByte = startSample * bytesPerIQ;

        // load time signal
        int outputSize = (int) Math.floor(count / down);
        double[] inReal = new double[count];
        double[] inImag = new double[count];

        for (int ind = 0; ind < count; ind++) {
            // Map the new index back to the original buffer index

            long byteOffset = startByte + (ind * bytesPerIQ);

            double real, imag;
            if (datatype.startsWith("ci16")) {
                real = buffer.getShort((int) byteOffset) / 32768.0;
                imag = buffer.getShort((int) byteOffset + 2) / 32768.0;
            } else {
                real = buffer.getFloat((int) byteOffset);
                imag = buffer.getFloat((int) byteOffset + 4);
            }
            inReal[ind] = real;
            inImag[ind] = imag;
        }

        double[][] result;
        if (fast) {
            // uses a polyphase downconverter with a moving average filter prior to decimation
            result = Resampler.downConvertPolyphase(inReal, inImag, freqOff, 1.0, down);
            logger.info("Downconverter (fast) completed!!");
        } else {
            // downconvert (LPF - downconvert)
            // NOTE: this has better stopband attenuation but is slower.
            Resampler resampler = new Resampler(1, down); // just use 1 (for up factor)
            result = resampler.downConvert(inReal, inImag, freqOff, 1.0);
            logger.info("Downconverter (conventional) completed!!");
        }

        return result;
    }
}
