package dev.oculusforge.render.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.ColorBufferPlan;
import dev.oculusforge.render.program.ColorBufferFormat;
import dev.oculusforge.render.program.LegacyFinalShaderAdapter;
import dev.oculusforge.render.program.PackFinalProgram;
import dev.oculusforge.render.program.PackShaderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.FramePassManager.PassDefinition;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL33C;

public final class OculusCompositePass implements PassDefinition {
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0F);
    private final ShaderPipelineManager manager;
    private ResourceHandle<RenderTarget> mainHandle = ResourceHandle.invalid();
    private final List<ResourceHandle<RenderTarget>> scratchHandles = new ArrayList<>();
    private final java.util.Map<Integer, GpuTextureView> borrowedColorViews = new java.util.HashMap<>();
    private PackShaderPipeline extractedPipeline;
    private PackShaderPipeline.Variant extractedVariant;
    private RuntimeException extractionFailure;
    private String lastLoggedVariant = "";
    private int diagnosticFrames;
    private boolean diagnosticCaptured;
    private final java.util.Map<ColorBufferFormat, RenderTargetDescriptor> scratchDescriptors = new java.util.EnumMap<>(ColorBufferFormat.class);

    public OculusCompositePass(ShaderPipelineManager manager) {
        this.manager = manager;
    }

    @Override
    public void extracts(LevelTargetBundle bundle, FramePass pass, DeltaTracker deltaTracker) {
        scratchHandles.clear();
        borrowedColorViews.clear();
        extractionFailure = null;
        extractedVariant = null;
        extractedPipeline = manager.activePipeline();
        if (extractedPipeline == null) return;
        if (extractedPipeline.world() != null && dev.oculusforge.render.world.WorldRenderHooks.frameDeferred()) return;
        try {
            extractedVariant = extractedPipeline.variant(currentProgramRoot());
            if (extractedVariant == null) return; // No shaders for this dimension: leave vanilla untouched.
            RenderTarget main = bundle.main.get();
            GpuTexture color = Objects.requireNonNull(main.getColorTexture(), "Main color texture");
            if (color.getFormat() != GpuFormat.RGBA8_UNORM
                && (!dev.oculusforge.render.world.WorldSceneCapture.isMain(main)
                    || color.getFormat()!=dev.oculusforge.render.world.WorldSceneCapture.format(main).gpu())) {
                throw new IllegalStateException("Post-processing currently requires an RGBA8 main target");
            }
            ColorBufferPlan buffers = extractedVariant.buffers();
            buffers.checkMemoryBudget(main.width, main.height);
            bundle.main = pass.readsAndWrites(bundle.main);
            mainHandle = bundle.main;
            for (int slot = 0; slot < buffers.scratchCount(); slot++) {
                scratchHandles.add(pass.createsInternal("oculus_color_" + slot, descriptorFor(main, buffers.scratchFormats().get(slot))));
            }
        } catch (RuntimeException exception) {
            extractionFailure = exception;
            // A pre-allocation failure has no resource writes, so the framegraph
            // would otherwise cull the callback that deactivates the broken pack.
            pass.disableCulling();
        }
    }

    public void executes(LevelRenderState state) {
        if (extractionFailure != null) {
            manager.failRuntime(extractionFailure);
            return;
        }
        PackShaderPipeline.Variant variant = extractedVariant;
        if (variant == null) return;
        if (manager.activePipeline() != extractedPipeline) return; // A world-render failure already closed this generation.
        // Post-only packs also need the current camera extraction. A missing snapshot
        // is transient (e.g. world entry), not a reason to disable the selected pack.
        if (((dev.oculusforge.render.world.WorldEnvironmentState) state.cameraRenderState).oculus$getEnvironment() == null) return;
        try {
            RenderTarget main = mainHandle.get();
            ColorBufferPlan buffers = variant.buffers();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (var initial : buffers.borrowedColorFormats().entrySet()) {
                GpuTextureView view = dev.oculusforge.render.world.WorldRenderHooks.currentColorView(initial.getKey());
                if (view.texture().getFormat() != initial.getValue().gpu()) throw new IllegalStateException("World/post color-format mismatch");
                if (view.getWidth(0) != main.width || view.getHeight(0) != main.height) throw new IllegalStateException("World/post color-size mismatch");
                borrowedColorViews.put(initial.getKey(), view);
            }
            PostStageCapture capture = null;
            if (Boolean.getBoolean("oculus.diagnostic.capturePostStages") && !diagnosticCaptured
                && currentProgramRoot().equals(System.getProperty("oculus.diagnostic.captureRoot", "shaders/world1"))
                && ++diagnosticFrames >= 120) {
                diagnosticCaptured = true;
                try { capture = new PostStageCapture(main.width, main.height); }
                catch (Exception failure) { OculusForge.LOGGER.warn("Could not start post capture", failure); }
            }
            try (PostStageCapture activeCapture = capture) {
                if (activeCapture != null) activeCapture.capture(encoder, main.getColorTextureView(), "00-world-before-post");
                for (int index = 0; index < variant.programs().size(); index++) {
                    var step = buffers.steps().get(index);
                    var program = variant.programs().get(index);
                    executeProgram(encoder, program, step, state, main);
                    if (activeCapture != null) {
                        for (int output = 0; output < step.outputSlots().size(); output++) {
                            activeCapture.capture(encoder, colorView(step.outputSlots().get(output), main),
                                String.format("%02d-%s-output%d", index + 1, program.stageName(), output));
                        }
                    }
                }
            }

            // Retain only successful final logical versions. Borrowed world storage
            // remains immutable until every post stage has finished reading it.
            for (var retained : buffers.retainedColorSlots().entrySet()) {
                GpuTexture source = Objects.requireNonNull(scratchHandles.get(retained.getValue()).get().getColorTexture());
                GpuTexture destination = dev.oculusforge.render.world.WorldRenderHooks.currentColorTexture(retained.getKey());
                if (source.getFormat() != destination.getFormat()) throw new IllegalStateException("History color-format mismatch");
                encoder.copyTextureToTexture(source, destination, 0, 0, 0, 0, 0, main.width, main.height);
            }

            // Commit exactly once, after every stage succeeds. An earlier failure cannot
            // leave half-processed pixels in the vanilla framebuffer.
            if (buffers.presentedSlot() != ColorBufferPlan.MAIN) {
                GpuTexture result = Objects.requireNonNull(scratchHandles.get(buffers.presentedSlot()).get().getColorTexture());
                if (result.getFormat() == Objects.requireNonNull(main.getColorTexture()).getFormat()) {
                    encoder.copyTextureToTexture(result, main.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
                } else {
                    dev.oculusforge.render.program.FullscreenColorTransfer.present(encoder,
                        Objects.requireNonNull(scratchHandles.get(buffers.presentedSlot()).get().getColorTextureView()),
                        Objects.requireNonNull(main.getColorTextureView()),
                        dev.oculusforge.render.world.WorldSceneCapture.isMain(main)
                            ? dev.oculusforge.render.world.WorldSceneCapture.format(main) : ColorBufferFormat.RGBA8);
                }
            }

            String key = extractedPipeline.packId() + ":" + currentProgramRoot();
            if (!key.equals(lastLoggedVariant)) {
                lastLoggedVariant = key;
                OculusForge.LOGGER.info("Executed post-processing '{}' ({} stages, {} scratch targets) at {}x{}",
                    key, variant.programs().size(), buffers.scratchCount(), main.width, main.height);
                OculusForge.LOGGER.info("Oculus post target formats: {} ({} bytes)", buffers.scratchFormats(), buffers.scratchBytes(main.width, main.height));
                OculusForge.LOGGER.info("Oculus post reads {} world attachments directly (no import copies)", buffers.borrowedColorFormats().size());
                OculusForge.LOGGER.info("Oculus retained auxiliary history: {}", buffers.retainedColorSlots().keySet());
            }
        } catch (RuntimeException exception) {
            // A failed sampler setup can leave Minecraft's tracked active texture
            // unit pointing beyond its fixed twelve-entry array. Restore unit zero
            // before any vanilla draw is allowed to continue.
            GlStateManager._activeTexture(GL33C.GL_TEXTURE0);
            manager.failRuntime(exception);
        }
    }

    private void executeProgram(CommandEncoder encoder, PackFinalProgram program, ColorBufferPlan.Step step,
                                LevelRenderState state, RenderTarget main) {
        boolean uniformFrameStarted = false;
        try {
            GpuBuffer uniforms = null;
            if (program.hasUniforms()) {
                uniforms = program.updateUniforms(state, main.width, main.height, extractedVariant.eyeBrightness(),
                    extractedVariant.customUniforms(),extractedVariant.cameraHistory());
                uniformFrameStarted = true;
            }
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Oculus post-processing " + program.stageName());
            for (int outputSlot : step.outputSlots()) descriptor.withColorAttachment(colorView(outputSlot, main),
                Optional.of(extractedVariant.buffers().scratchFormats().get(outputSlot).clearColor()));
            GpuTextureView firstOutput = colorView(step.outputSlot(), main);
            try (RenderPass renderPass = encoder.createRenderPass(descriptor.withRenderArea(
                    new RenderPass.RenderArea(0, 0, firstOutput.getWidth(0), firstOutput.getHeight(0))))) {
                renderPass.setPipeline(program.pipeline());
                if (uniforms != null) renderPass.setUniform("OculusLegacyUniforms", uniforms);
                GpuSampler colorSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                for (LegacyFinalShaderAdapter.SamplerBinding binding : program.samplers()) {
                    switch (binding.texture()) {
                        case COLOR -> {
                            Integer worldIndex = step.worldColorInputs().get(binding.name());
                            GpuTextureView input = worldIndex == null ? colorView(step.colorInputs().get(binding.name()), main)
                                : Objects.requireNonNull(borrowedColorViews.get(worldIndex), "Missing borrowed world attachment");
                            renderPass.bindTexture(binding.name(), input, colorSampler);
                        }
                        case CUSTOM -> {
                            var texture = extractedPipeline.image(binding.imageKey());
                            renderPass.bindTexture(binding.name(), texture.view(), texture.sampler());
                        }
                        case DEPTH -> renderPass.bindTexture(binding.name(), binding.colorIndex() == 1
                            ? dev.oculusforge.render.world.WorldRenderHooks.currentOpaqueWorldDepthView()
                            : requireDepthView(main), depthSampler);
                        case SHADOW_DEPTH -> renderPass.bindTexture(binding.name(),
                            dev.oculusforge.render.world.WorldRenderHooks.currentShadowDepthView(binding.colorIndex()), depthSampler);
                        case SHADOW_COLOR -> {
                            renderPass.bindTexture(binding.name(),
                                dev.oculusforge.render.world.WorldRenderHooks.currentShadowColorView(binding.colorIndex()), colorSampler);
                        }
                        case NOISE -> renderPass.bindTexture(binding.name(), extractedPipeline.noiseView(),
                            RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                    }
                }
                renderPass.draw(3, 1, 0, 0);
            }
        } finally {
            if (uniformFrameStarted) program.finishFrame();
        }
    }

    private GpuTextureView colorView(int slot, RenderTarget main) {
        RenderTarget target = slot == ColorBufferPlan.MAIN ? main : scratchHandles.get(slot).get();
        return Objects.requireNonNull(target.getColorTextureView(), "Post-processing color view");
    }

    private static GpuTextureView requireDepthView(RenderTarget target) {
        if (target.useDepth && target.getDepthTextureView() != null) return target.getDepthTextureView();
        throw new IllegalStateException("Shader requests depthtex0 but the main target has no depth texture");
    }

    private static String currentProgramRoot() {
        var level = Minecraft.getInstance().level;
        if (level == null) return "shaders";
        if (level.dimension().equals(Level.OVERWORLD)) return "shaders/world0";
        if (level.dimension().equals(Level.NETHER)) return "shaders/world-1";
        if (level.dimension().equals(Level.END)) return "shaders/world1";
        return "shaders";
    }

    private RenderTargetDescriptor descriptorFor(RenderTarget target, ColorBufferFormat format) {
        RenderTargetDescriptor descriptor = scratchDescriptors.get(format);
        if (descriptor == null || descriptor.width() != target.width || descriptor.height() != target.height || descriptor.format() != format.gpu()) {
            descriptor = new RenderTargetDescriptor(target.width, target.height, false, new Vector4f(format.clearColor()), format.gpu());
            scratchDescriptors.put(format, descriptor);
        }
        return descriptor;
    }
}
