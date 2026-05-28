package net.kcundercover.spectral_analyzer.data;

public enum Endianness {
    LITTLE_ENDIAN("_le"),
    BIG_ENDIAN("_be"),
    NOT_APPLICABLE(""); // Used for 8-bit formats like cu8/ci8 where byte-order doesn't exist

    private final String suffix;

    Endianness(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }
}
