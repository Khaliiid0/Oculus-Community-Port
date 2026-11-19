package dev.oculusforge.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OculusConfigStoreTest {
    @TempDir Path directory;

    @Test void enabledWithoutASelectionNormalizesToDisabled() {
        OculusConfig config = new OculusConfig(true, "   ");
        assertFalse(config.shadersEnabled());
        assertEquals("", config.selectedPack());
    }

    @Test void unsafeOrNonPortablePackNamesAreRejected() {
        for (String name : new String[]{"../pack", "folder/pack", "folder\\pack", "bad\nname",
            "bad\u0000name", "bad:name", "bad*name", "bad?name", "bad|name", "bad<name", "bad>name", "bad\"name"}) {
            assertThrows(IllegalArgumentException.class, () -> new OculusConfig(true, name), name);
        }
        assertEquals("Shader Pack [v2].zip", new OculusConfig(true, " Shader Pack [v2].zip ").selectedPack());
    }

    @Test void malformedPersistedValuesFailClosed() throws Exception {
        Path path = directory.resolve("oculus.properties");
        Files.writeString(path, "shaders.enabled=maybe\nshaderpack=good.zip\n");
        assertEquals(new OculusConfig(false, "good.zip"), new OculusConfigStore(path).load());
        Files.writeString(path, "shaders.enabled=true\nshaderpack=../unsafe\n");
        assertEquals(OculusConfig.DEFAULT, new OculusConfigStore(path).load());
    }

    @Test void oversizedConfigurationIsRejectedWithoutParsingIt() throws Exception {
        Path path = directory.resolve("oculus.properties");
        Files.writeString(path, "x".repeat(OculusConfigStore.MAX_CONFIG_BYTES + 1));
        assertThrows(IOException.class, () -> new OculusConfigStore(path).load());
    }

    @Test void saveRoundTripsAndDoesNotLeaveATemporaryFile() throws Exception {
        Path path = directory.resolve("nested/oculus.properties");
        OculusConfig expected = new OculusConfig(true, "pack.zip");
        OculusConfigStore store = new OculusConfigStore(path);
        store.save(expected);
        assertEquals(expected, store.load());
        try (var files = Files.list(path.getParent())) {
            assertEquals(java.util.List.of("oculus.properties"),
                files.map(item -> item.getFileName().toString()).sorted().toList());
        }
    }
}
