package net.kcundercover.spectral_analyzer.data;

public record RawSignalImportSettings(
    String datatype,
    double sampleRate,
    long centerFrequency,
    String timestamp
) { }
