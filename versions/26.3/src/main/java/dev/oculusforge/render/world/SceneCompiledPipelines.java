package dev.oculusforge.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.oculusforge.port.CompiledPipelineRetargeter;
import dev.oculusforge.render.program.ColorBufferFormat;

/** CPU links only; Minecraft's PipelineCache owns and disposes the returned GPU pipelines. */
public final class SceneCompiledPipelines {
    private static final CompiledPipelineRetargeter LINKS = new CompiledPipelineRetargeter(512);
    private SceneCompiledPipelines() {}

    public static void remember(RenderPipeline source, CompiledRenderPipeline compiled) {
        if (SceneColorFormat.ENABLED) LINKS.remember(source, compiled);
    }

    public static CompiledRenderPipeline adapt(CompiledRenderPipeline original, ColorBufferFormat format, int mask) {
        RenderSystem.assertOnRenderThread();
        return LINKS.retarget(original,format.gpu(),format.writeMask(),mask,
            source -> ScenePipelineFormats.adapt(source,format,mask), RenderSystem::getCompiledPipeline);
    }

    public static void reset() { RenderSystem.assertOnRenderThread(); LINKS.clear(); }
}
