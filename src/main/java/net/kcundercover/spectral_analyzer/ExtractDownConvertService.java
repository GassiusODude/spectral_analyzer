package net.kcundercover.spectral_analyzer;

import org.apache.commons.math3.complex.Complex;
import org.springframework.stereotype.Service;
import java.nio.MappedByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.jdsp.signal.Resampler;


@Service
public class ExtractDownConvertService {
    private static final Logger logger = LoggerFactory.getLogger(ExtractDownConvertService.class);

    /**
     * Extracts IQ samples and performs a simple down-conversion/resample.
     */
    public double[][] extractAndDownConvert(MappedByteBuffer buffer, long startSample,
                                           int count, String datatype, double freqOff, int down) {

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

        // downconvert
        double[][] result = Resampler.downConvertPolyphase(inReal, inImag, freqOff, 1.0, down);
        logger.info("Resample complted!!");
        return result;
    }
}
