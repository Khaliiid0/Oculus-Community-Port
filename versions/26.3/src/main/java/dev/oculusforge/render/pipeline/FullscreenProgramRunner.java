package dev.oculusforge.render.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.render.program.*;
import dev.oculusforge.render.world.WorldRenderHooks;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Shared draw path for both fullscreen phases; callers own storage and commit. */
final class FullscreenProgramRunner {
    private FullscreenProgramRunner() { }

    static void execute(CommandEncoder encoder, PackFinalProgram program, ColorBufferPlan.Step step,
                        LevelRenderState state, RenderTarget main, PackShaderPipeline pipeline,
                        PackShaderPipeline.Variant variant, ColorBufferPlan buffers,
                        IntFunction<GpuTextureView> colors, Map<Integer, GpuTextureView> borrowed) {
        boolean started = false;
        Throwable failure = null;
        try {
            com.mojang.renderpearl.api.buffers.GpuBuffer uniforms = null;
            if (program.hasUniforms()) {
                uniforms = program.updateUniforms(state, main.width, main.height, variant.eyeBrightness(),
                    variant.customUniforms(), variant.cameraHistory(),variant.wetness());
                started = true;
            }
            var descriptor = RenderPassDescriptor.builder(() -> "Oculus fullscreen " + program.stageName());
            for (int output : step.outputSlots()) descriptor.withColorAttachment(colors.apply(output),
                Optional.of(buffers.scratchFormats().get(output).clearColor()));
            var first = colors.apply(step.outputSlot());
            try (var pass = encoder.createRenderPass(descriptor.withRenderArea(
                new RenderPass.RenderArea(0, 0, first.getWidth(0), first.getHeight(0))).build())) {
                pass.setPipeline(program.pipeline());
                if (uniforms != null) pass.setUniform("OculusLegacyUniforms", uniforms);
                var colorSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                var depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                for (var binding : program.samplers()) switch (binding.texture()) {
                    case COLOR -> {
                        Integer world = step.worldColorInputs().get(binding.name());
                        var input = world == null ? colors.apply(step.colorInputs().get(binding.name()))
                            : Objects.requireNonNull(borrowed.get(world), "Missing borrowed world attachment");
                        pass.setUniform(binding.name(), input, colorSampler);
                    }
                    case CUSTOM -> {
                        var texture = pipeline.image(binding.imageKey());
                        pass.setUniform(binding.name(), texture.view(), texture.sampler());
                    }
                    case DEPTH -> pass.setUniform(binding.name(), WorldDepthSamplers.resolve(binding.colorIndex(),
                        main::getDepthTextureView,WorldRenderHooks::currentOpaqueWorldDepthView), depthSampler);
                    case SHADOW_DEPTH -> pass.setUniform(binding.name(),
                        WorldRenderHooks.currentShadowDepthView(binding.colorIndex()), depthSampler);
                    case SHADOW_COLOR -> pass.setUniform(binding.name(),
                        WorldRenderHooks.currentShadowColorView(binding.colorIndex()), colorSampler);
                    case NOISE -> pass.setUniform(binding.name(), pipeline.noiseView(),
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                    case CENTER_DEPTH -> pass.setUniform(binding.name(),Objects.requireNonNull(variant.centerDepth()).view(
                        net.minecraft.client.Minecraft.getInstance().level,RenderAnimationClock.current()),depthSampler);
                }
                pass.draw(3, 1, 0, 0);
            }
        } catch(RuntimeException|Error problem) {
            failure=problem;throw problem;
        } finally {
            if(started)dev.oculusforge.port.ResourceDisposal.closeAfter(failure,program::finishFrame);
        }
    }
}
