package net.kcundercover.spectral_analyzer.data;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Set;

public class SecureAppStorage {

    private static final String APP_FOLDER = "SpectralAnalyzer";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * Saves general, non-sensitive application settings (plain text).
     */
    public static void saveAppConfig(String configJson) {
        try {
            Path configFile = Paths.get(getSystemAppDataPath()).resolve("config.json");
            Path parentDir = configFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(configFile, configJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to save app configuration: " + e.getMessage());
        }
    }

    /**
     * Loads plain text app configuration. Returns null if missing.
     */
    public static String loadAppConfig() {
        Path configFile = Paths.get(getSystemAppDataPath()).resolve("config.json");
        if (!Files.exists(configFile)) {
            return null;
        }
        try {
            return Files.readString(configFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            System.err.println("Failed to load app configuration: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolves standard Windows AppData/Roaming or Ubuntu .config directory path.
     */
    public static String getSystemAppDataPath() {
        String basePath = IS_WINDOWS ? System.getenv("APPDATA") : System.getenv("XDG_CONFIG_HOME");

        // Fallbacks if system environment variables are empty
        if (basePath == null || basePath.isEmpty()) {
            basePath = System.getProperty("user.home") + (IS_WINDOWS ? "\\AppData\\Roaming" : "/.config");
        }
        return basePath + File.separator + APP_FOLDER;
    }

    /**
     * Saves files securely based on the running OS.
     */
    public static void saveSessionData(String jwtToken, String certThumbprint) throws IOException {
        Path targetDir = Paths.get(getSystemAppDataPath());
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path tokenFile = targetDir.resolve("session.dat");
        Path certFile = targetDir.resolve("certs.dat");

        if (IS_WINDOWS) {
            // --- WINDOWS SETUP (DPAPI Encryption via JNA) ---
            byte[] encToken = Crypt32Util.cryptProtectData(jwtToken.getBytes(StandardCharsets.UTF_8));
            byte[] encCert = Crypt32Util.cryptProtectData(certThumbprint.getBytes(StandardCharsets.UTF_8));

            Files.writeString(tokenFile, Base64.getEncoder().encodeToString(encToken));
            Files.writeString(certFile, Base64.getEncoder().encodeToString(encCert));
        } else {
            // --- UBUNTU SETUP (Strict POSIX User-Lock) ---
            Files.writeString(tokenFile, jwtToken);
            Files.writeString(certFile, certThumbprint);

            try {
                // Instantly restricts files to Owner Read/Write Only (chmod 600)
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(tokenFile, perms);
                Files.setPosixFilePermissions(certFile, perms);
            } catch (UnsupportedOperationException e) {
                // Safe catch if running on a non-POSIX drive formatting
            }
        }
    }

    /**
     * Loads and decrypts/reads the stored JWT. Returns null if missing or corrupted.
     */
    public static String loadSecureJWT() {
        Path tokenFile = Paths.get(getSystemAppDataPath()).resolve("session.dat");
        if (!Files.exists(tokenFile)) {
            return null;
        }
        try {
            String content = Files.readString(tokenFile).trim();

            if (IS_WINDOWS) {
                // Decrypt Windows bytes via OS Kernel hooks
                byte[] encryptedBytes = Base64.getDecoder().decode(content);
                byte[] decryptedBytes = Crypt32Util.cryptUnprotectData(encryptedBytes);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } else {
                // Ubuntu reads the file directly (protected by OS file security permissions)
                return content;
            }
        } catch (Exception e) {
            System.err.println("Failed to read secure token: " + e.getMessage());
            return null;
        }
    }
}
