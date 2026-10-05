package org.herb.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Loads ignored local settings consistently for IDE and command-line starts. */
public final class LocalEnvironment {
    private static final Set<String> ALLOWED = Set.of("DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "HERB_JWT_SECRET", "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET", "ZHIPU_API_KEY",
            "HERB_BIND_ADDRESS", "HERB_API_DOCS_ENABLED", "HERB_ALLOWED_ORIGINS",
            "REDIS_HOST", "REDIS_PORT", "SPRING_DATA_REDIS_PASSWORD");

    private LocalEnvironment() {}

    public static String get(String name) {
        String value = System.getenv(name);
        return value != null ? value : System.getProperty(name);
    }

    public static void load() {
        Path settings = Path.of(".env.local").toAbsolutePath();
        if (!Files.isRegularFile(settings)) {
            try {
                Path classes = Path.of(LocalEnvironment.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                if (Files.isDirectory(classes) && classes.getParent() != null
                        && classes.getParent().getParent() != null) {
                    settings = classes.getParent().getParent().resolve(".env.local");
                }
            } catch (Exception ignored) {
                return; // Packaged deployments use explicitly supplied environment variables.
            }
        }
        load(settings);
    }

    static void load(Path settings) {
        if (!Files.isRegularFile(settings)) return;
        try {
            for (String line : Files.readAllLines(settings, StandardCharsets.UTF_8)) {
                line = line.replaceFirst("^\\uFEFF", "");
                if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException("Invalid local configuration entry");
                String name = line.substring(0, separator).trim();
                if (!ALLOWED.contains(name)) throw new IllegalArgumentException("Unsupported local configuration key: " + name);
                if (get(name) == null) System.setProperty(name, line.substring(separator + 1));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read local configuration", e);
        }
    }
}
