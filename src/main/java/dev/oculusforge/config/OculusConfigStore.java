package dev.oculusforge.config;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public final class OculusConfigStore {
   static final int MAX_CONFIG_BYTES = 65536;
   private static final String ENABLED = "shaders.enabled";
   private static final String PACK = "shaderpack";
   private final Path path;

   public OculusConfigStore(Path path) {
      this.path = path.toAbsolutePath().normalize();
   }

   public Path path() {
      return this.path;
   }

   public OculusConfig load() throws IOException {
      if (!Files.exists(this.path)) {
         return OculusConfig.DEFAULT;
      } else {
         Properties properties = new Properties();

         try (InputStream input = new ByteArrayInputStream(readBounded(this.path))) {
            properties.load(input);
         }

         String enabledValue = properties.getProperty("shaders.enabled", "false").trim();

         boolean enabled = switch (enabledValue) {
            case "true" -> true;
            case "false" -> false;
            default -> false;
         };

         try {
            return new OculusConfig(enabled, properties.getProperty("shaderpack", ""));
         } catch (IllegalArgumentException var7) {
            return OculusConfig.DEFAULT;
         }
      }
   }

   public void save(OculusConfig config) throws IOException {
      Path parent = this.path.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Path temporary = Files.createTempFile(parent, this.path.getFileName().toString(), ".tmp");
      boolean moved = false;

      try {
         Properties properties = new Properties();
         properties.setProperty("shaders.enabled", Boolean.toString(config.shadersEnabled()));
         properties.setProperty("shaderpack", config.selectedPack());

         try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Unofficial Oculus Forge configuration");
         }

         try {
            Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException var15) {
            Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
         }

         moved = true;
      } finally {
         if (!moved) {
            Files.deleteIfExists(temporary);
         }
      }
   }

   private static byte[] readBounded(Path path) throws IOException {
      try (InputStream input = Files.newInputStream(path);
           ByteArrayOutputStream output = new ByteArrayOutputStream(4096)) {
         byte[] buffer = new byte[4096];
         int total = 0;
         for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
            total = Math.addExact(total, count);
            if (total > MAX_CONFIG_BYTES) {
               throw new IOException("Oculus configuration exceeds " + MAX_CONFIG_BYTES + " bytes");
            }
            output.write(buffer, 0, count);
         }
         return output.toByteArray();
      } catch (ArithmeticException exception) {
         throw new IOException("Oculus configuration size overflow", exception);
      }
   }
}
