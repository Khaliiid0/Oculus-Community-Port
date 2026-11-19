package dev.oculusforge.render.pipeline;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.FullscreenColorTransfer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in, bounded, single-frame diagnostic. Never changes the source attachment. */
public final class PostStageCapture implements AutoCloseable {
    private final TextureTarget target;
    private final Path directory;

    PostStageCapture(int width, int height) throws Exception {
        this(width, height, "post-stages-");
    }

    public PostStageCapture(int width, int height, String prefix) throws Exception {
        if (!prefix.equals("post-stages-") && !prefix.equals("world-stages-"))
            throw new IllegalArgumentException("Unknown diagnostic capture prefix");
        FullscreenColorTransfer.compile();
        Path parent = Minecraft.getInstance().gameDirectory.toPath().resolve("oculus-diagnostics");
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, prefix);
        target = new TextureTarget("Oculus diagnostic conversion", width, height, GpuFormat.RGBA8_UNORM, null);
        OculusForge.LOGGER.info("Oculus one-frame capture: {}", directory);
    }

    public void capture(CommandEncoder encoder, GpuTextureView source, String label) {
        FullscreenColorTransfer.present(encoder, source, target.getColorTextureView());
        Path file = directory.resolve(label.replaceAll("[^A-Za-z0-9_-]", "_") + ".png");
        // Enqueue readback before this conversion target is reused. PNGs are clamped
        // RGBA8 previews, not lossless HDR measurements.
        Screenshot.takeScreenshot(target, image -> {
            try (image) {
                image.writeToFile(file);
            } catch (Exception failure) {
                OculusForge.LOGGER.warn("Could not save diagnostic {}", file, failure);
            }
        });
    }

    @Override public void close() { target.destroyBuffers(); }
}
