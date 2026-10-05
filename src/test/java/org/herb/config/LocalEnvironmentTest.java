package org.herb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalEnvironmentTest {
    @TempDir Path directory;

    @Test
    void readsUtf8SettingsAndPreservesExplicitOverrides() throws Exception {
        String name = "HERB_ALLOWED_ORIGINS";
        assumeTrue(System.getenv(name) == null);
        String original = System.getProperty(name);
        Path settings = directory.resolve(".env.local");
        try {
            System.clearProperty(name);
            Files.writeString(settings, "\uFEFF# local settings\n" + name + "=https://example.test/?a=b\n");
            LocalEnvironment.load(settings);
            assertEquals("https://example.test/?a=b", LocalEnvironment.get(name));
            System.setProperty(name, "explicit");
            LocalEnvironment.load(settings);
            assertEquals("explicit", LocalEnvironment.get(name));
        } finally {
            if (original == null) System.clearProperty(name);
            else System.setProperty(name, original);
        }
    }
}
