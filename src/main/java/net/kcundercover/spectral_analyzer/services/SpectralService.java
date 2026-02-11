package net.kcundercover.spectral_analyzer.services;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;
import java.nio.MappedByteBuffer;

/**
 * Service to perform FFT
 *
 * This service processes the FFT on an input buffer.
 */
@Service
public class SpectralService {

    private final FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

    /**
     * Processes a chunk of IQ data into power magnitudes (dB).
     * @param buffer The SigMF MappedByteBuffer
     * @param startByte The byte offset to start reading
     * @param n The number of samples (MUST be a power of 2 for Apache Commons)
     * @return Array of magnitudes for the spectrogram
     */
    public double[] computeMagnitudes(MappedByteBuffer buffer, int startByte, int n, String datatype) {
        Complex[] complexData = new Complex[n];
        boolean isCi16 = datatype.startsWith("ci16");

        for (int i = 0; i < n; i++) {
            double real, imag;
            if (isCi16) {
                // Read 2-byte shorts for ci16 (Total 4 bytes per IQ pair)
                real = buffer.getShort(startByte + (i * 4)) / 32768.0;
                imag = buffer.getShort(startByte + (i * 4) + 2) / 32768.0;
            } else {
                // Read 4-byte floats for cf32 (Total 8 bytes per IQ pair)
                real = buffer.getFloat(startByte + (i * 8));
                imag = buffer.getFloat(startByte + (i * 8) + 4);
            }
            complexData[i] = new Complex(real, imag);
        }

        // calculate FFT (frequencies are from 0 to FS)
        Complex[] fftResult = transformer.transform(complexData, TransformType.FORWARD);

        // Apply FFT Shift (make frequency range from -fs/2 to fs/2)
        double[] shiftedMagnitudes = new double[n];
        int half = n / 2;

        for (int i = 0; i < n; i++) {
            // Swap halves: index i becomes (i + half) % n
            int shiftedIndex = (i + half) % n;

            double abs = fftResult[i].abs();
            shiftedMagnitudes[shiftedIndex] = 20 * Math.log10(abs + 1e-10);
        }

        return shiftedMagnitudes;
    }
}
