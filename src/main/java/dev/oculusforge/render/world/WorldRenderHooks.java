package dev.oculusforge.render.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.pipeline.ShaderPipelineManager;
import dev.oculusforge.render.pipeline.PostStageCapture;
import dev.oculusforge.render.program.PackShaderPipeline;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector4fc;

/** Narrow static seam used by renderer mixins. All mutable state remains render-thread confined. */
public final class WorldRenderHooks {
    private static final ThreadLocal<FrameState> FRAME = ThreadLocal.withInitial(FrameState::new);
    private static volatile ShaderPipelineManager manager;
    private static boolean endSkyEntryLogged;
    private static String lastSkyFrameDiagnostic = "";
    private static int diagnosticFrames;
    private static boolean diagnosticCaptured;
    private static PostStageCapture worldCapture;

    private WorldRenderHooks() {}

    public static void install(ShaderPipelineManager pipelineManager) { manager = pipelineManager; }

    public static boolean beginSceneCapture() {
        if(!SceneColorFormat.ENABLED)return false;
        var pipeline=activeWorld();
        var variant=pipeline==null?null:pipeline.variant(currentProgramRoot());
        if(variant==null||!SceneColorFormat.requiresCapture(variant.plan().formats()))return false;
        try {
            SceneColorFormat.requireSupported(variant.plan().formats(),Minecraft.getInstance().gameRenderer
                .gameRenderState().optionsRenderState.improvedTransparency);
            var original=Minecraft.getInstance().gameRenderer.mainRenderTarget();
            var targets=pipeline.ensureTargets(original.width,original.height,variant);
            WorldSceneCapture.begin(pipeline,original,targets.colorTexture(0),targets.colorView(0),
                variant.plan().formats().get(0));
            return true;
        }catch(RuntimeException failure){failSceneCapture(failure);return false;}
    }
    public static void failSceneCapture(RuntimeException failure) { failWorld("HDR scene capture failed",failure); }
    public static void endSceneCapture() {
        try { WorldSceneCapture.end(); }
        catch(RuntimeException failure) { failSceneCapture(failure); }
    }

    public static void selectEndSkyPipeline(RenderPass pass, RenderPipeline vanilla) {
        FrameState frame = FRAME.get();
        WorldRenderPipeline pipeline = activeWorld();
        if (Boolean.getBoolean("oculus.experimental.nativeEndSky") && !endSkyEntryLogged) {
            OculusForge.LOGGER.info("Oculus End sky hook entered: world={}, nativeSky={}",
                pipeline != null, pipeline != null && pipeline.endSky() != null);
            endSkyEntryLogged = true;
        }
        if (pipeline != null && pipeline.endSky() != null)
            pipeline.noteEndSkyReadiness(frame.prepared, frame.deferred, currentProgramRoot());
        if (!frame.prepared || frame.deferred || pipeline == null || pipeline.endSky() == null
            || !currentProgramRoot().equals("shaders/world1")) {
            pass.setPipeline(vanilla);
            return;
        }
        try {
            pass.setPipeline(pipeline.endSky().pipeline());
            if (pipeline.endSky().usesWorldMatrices()) pipeline.bindMatrices(pass);
            pipeline.noteEndSkySubmission();
        } catch (RuntimeException failure) {
            failWorld("Native End sky binding failed", failure);
            pass.setPipeline(vanilla);
        }
    }

    public static void prepareFrame(LevelRenderer renderer, ChunkSectionsToRender mainSections,
        net.minecraft.client.renderer.state.level.LevelRenderState state) {
        RenderSystem.assertOnRenderThread();
        FrameState frame = FRAME.get();
        closeWorldCapture();
        frame.reset();
        frame.mainSections = mainSections;
        WorldRenderPipeline pipeline = activeWorld();
        WorldRenderPipeline.Variant variant = pipeline == null ? null : pipeline.variant(currentProgramRoot());
        if (Boolean.getBoolean("oculus.experimental.nativeEndSky")) {
            String diagnostic = currentProgramRoot() + ":skybox=" + state.skyRenderState.skybox
                + ",fog=" + state.cameraRenderState.fogType
                + ",effectBlocksSky=" + state.cameraRenderState.entityRenderState.doesMobEffectBlockSky;
            if (!lastSkyFrameDiagnostic.equals(diagnostic)) {
                OculusForge.LOGGER.info("Oculus sky frame: {}", diagnostic);
                lastSkyFrameDiagnostic = diagnostic;
            }
        }
        if (variant == null) return;
        float radius = variant.plan().shadowDistance();
        try {
            frame.shadowProjection = new Matrix4f().ortho(-radius, radius, -radius, radius, -radius * 4.0F, radius * 4.0F);
            frame.shadowModelView = ShadowView.fromCelestialAngles(
                state.skyRenderState.sunAngle, state.skyRenderState.moonAngle);
            Matrix4f shadowMatrix = new Matrix4f(frame.shadowProjection).mul(frame.shadowModelView);
            var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (!pipeline.updateMatrices(state, frame.shadowProjection, frame.shadowModelView, main.width, main.height,
                manager.activePipeline().variant(currentProgramRoot()).eyeBrightness(),
                manager.activePipeline().variant(currentProgramRoot()).customUniforms(),
                manager.activePipeline().variant(currentProgramRoot()).cameraHistory())) {
                // Do not bind stale uniforms or run post-processing on missing world outputs.
                // Retry next frame; this transient camera state is not a broken shader pack.
                frame.deferred = true;
                return;
            }
            pipeline.ensureTargets(main.width, main.height, variant).beginFrame();
            frame.prepared = true;
            if (variant.shadow() == null) return;
            frame.shadowSections = renderer.prepareChunkRenders(shadowMatrix);
        } catch (RuntimeException failure) {
            failWorld("Could not prepare world rendering", failure);
            frame.reset();
        }
    }

    public static void beginTerrainGroup(ChunkSectionsToRender sections, ChunkSectionLayerGroup group, GpuSampler sampler) {
        RenderSystem.assertOnRenderThread();
        FrameState frame = FRAME.get();
        if (frame.recursionDepth > 0) return;
        frame.mode = Mode.OFF;
        if (frame.deferred) return;
        try {
            WorldRenderPipeline pipeline = activeWorld();
            WorldRenderPipeline.Variant variant = pipeline == null ? null : pipeline.variant(currentProgramRoot());
            if (variant == null) return;
            var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            WorldRenderTargets targets = pipeline.ensureTargets(main.width, main.height, variant);
            frame.pipeline = pipeline;
            frame.variant = variant;
            frame.targets = targets;
            frame.group = group;

            if (group == ChunkSectionLayerGroup.OPAQUE && !diagnosticCaptured
                && Boolean.getBoolean("oculus.diagnostic.captureWorldStages")
                && currentProgramRoot().equals(System.getProperty("oculus.diagnostic.captureRoot", "shaders/world1"))
                && ++diagnosticFrames >= 120) {
                diagnosticCaptured = true;
                try {
                    worldCapture = new PostStageCapture(main.width, main.height, "world-stages-");
                } catch (Exception failure) {
                    OculusForge.LOGGER.warn("Could not start world-stage capture", failure);
                }
            }
            captureWorldStage(group == ChunkSectionLayerGroup.OPAQUE
                ? "00-before-opaque-and-shadow" : "03-before-translucent");

            if (group == ChunkSectionLayerGroup.OPAQUE && frame.shadowSections != null && !frame.shadowRendered && variant.shadow() != null) {
                frame.mode = Mode.SHADOW;
                frame.recursionDepth++;
                try {
                    for (var stage : pipeline.shadowTerrain().stages()) {
                        switch (stage) {
                            case OPAQUE -> frame.shadowSections.renderGroup(ChunkSectionLayerGroup.OPAQUE, sampler);
                            case SNAPSHOT -> targets.snapshotOpaqueShadowDepth();
                            case TRANSLUCENT -> frame.shadowSections.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, sampler);
                        }
                    }
                    // renderGroup has returned and its passes have closed before terrain can sample.
                    frame.shadowRendered = true;
                } finally {
                    frame.recursionDepth--;
                }
            }
            // A shadow-only pack still needs its shadow draw, but must leave the
            // main terrain pass on vanilla rather than inventing a gbuffer program.
            if (variant.usesShadowTextures())
                ShadowColorReadGuard.requireCompleted(false, frame.shadowRendered);
            if (group == ChunkSectionLayerGroup.OPAQUE) captureWorldStage("01-after-shadow-before-opaque");
            frame.mode = variant.plan().terrainProgram().isBlank() ? Mode.OFF : Mode.GBUFFER;
            if (group == ChunkSectionLayerGroup.TRANSLUCENT && pipeline.needsOpaqueWorldDepth(variant))
                targets.snapshotOpaqueWorldDepth(main.getDepthTexture());
        } catch (RuntimeException failure) {
            frame.mode = Mode.OFF;
            failWorld("World-render interception failed", failure);
        }
    }

    public static void endTerrainGroup() {
        FrameState frame = FRAME.get();
        if (frame.recursionDepth == 0) {
            if (frame.group != null) {
                captureWorldStage(frame.group == ChunkSectionLayerGroup.OPAQUE
                    ? "02-after-opaque" : "04-after-translucent");
                if (frame.group == ChunkSectionLayerGroup.TRANSLUCENT) closeWorldCapture();
            }
            frame.mode = Mode.OFF;
            frame.group = null;
            frame.variant = null;
            frame.targets = null;
            frame.pipeline = null;
        }
    }

    private static void captureWorldStage(String label) {
        if (worldCapture == null) return;
        try {
            worldCapture.capture(RenderSystem.getDevice().createCommandEncoder(),
                Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), label);
        } catch (RuntimeException failure) {
            OculusForge.LOGGER.warn("Could not capture world stage {}", label, failure);
            closeWorldCapture();
        }
    }

    private static void closeWorldCapture() {
        if (worldCapture == null) return;
        PostStageCapture capture = worldCapture;
        worldCapture = null;
        try {
            capture.close();
        } catch (RuntimeException failure) {
            OculusForge.LOGGER.warn("Could not close world-stage capture", failure);
        }
    }

    /** Draw only the opaque group, in vanilla layer/batch order, when attachments differ.
     * Every pass rebinds vanilla and native resources; no color/depth clear occurs here.
     * Shadows keep their own unsplit producer path and snapshot ordering.
     */
    public static boolean renderSplitOpaqueGroup(ChunkSectionsToRender sections,
        ChunkSectionLayerGroup group, GpuSampler sampler) {
        FrameState frame = FRAME.get();
        if (frame.mode != Mode.GBUFFER || frame.recursionDepth != 0
            || group != ChunkSectionLayerGroup.OPAQUE || frame.variant == null
            || !frame.variant.nativeLayersActive()
            || !frame.variant.plan().terrainLayers().requiresOpaquePassSplit()) return false;
        try {
            var autoIndices = RenderSystem.getSequentialBuffer(com.mojang.blaze3d.PrimitiveTopology.QUADS);
            var indexBuffer = sections.maxIndicesRequired() == 0 ? null : autoIndices.getBuffer(sections.maxIndicesRequired());
            var indexType = sections.maxIndicesRequired() == 0 ? null : autoIndices.type();
            var output = group.outputTarget();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            for (var layer : group.layers()) {
                var batches = sections.drawGroupsPerLayer().get(layer);
                if (batches == null || batches.values().stream().allMatch(java.util.List::isEmpty)) continue;
                try (RenderPass pass = createGbufferPass(encoder, () -> "Oculus split opaque " + layer,
                    output.getColorTextureView(), Optional.empty(), output.getDepthTextureView(), OptionalDouble.empty(),
                    frame.variant.terrainTargets(layer.pipeline()))) {
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.bindTexture("Sampler0", sections.textureView(), sampler);
                    pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                        RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
                    selectTerrainPipeline(pass, layer.pipeline());
                    for (var draws : batches.values()) {
                        if (!draws.isEmpty()) pass.drawMultipleIndexed(draws, indexBuffer, indexType,
                            java.util.List.of("ChunkSection"), sections.chunkSectionInfos());
                    }
                }
            }
        } catch (RuntimeException failure) {
            // Never replay partially submitted draws in the same frame.
            failWorld("Split opaque terrain rendering failed", failure);
        } finally {
            endTerrainGroup();
        }
        return true;
    }

    public static RenderPass createTerrainPass(CommandEncoder encoder, java.util.function.Supplier<String> label,
                                               GpuTextureView color, Optional<Vector4fc> clearColor,
                                               GpuTextureView depth, OptionalDouble clearDepth) {
        FrameState frame = FRAME.get();
        if (frame.mode == Mode.OFF) return encoder.createRenderPass(label, color, clearColor, depth, clearDepth);
        if (frame.mode == Mode.SHADOW) {
            frame.pipeline.noteShadowSubmission(currentProgramRoot(), frame.variant);
            var descriptor = RenderPassDescriptor.create(() -> "Oculus terrain shadow");
            // Fragment locations follow the pack's declared order, not sorted logical IDs.
            // beginFrame clears once; later passes must preserve earlier shadow geometry.
            for (int target : frame.variant.plan().shadowTargets())
                descriptor.withColorAttachment(frame.targets.shadowColorView(target), Optional.empty());
            return encoder.createRenderPass(descriptor
                    .withDepthAttachment(frame.targets.shadowDepthView(), OptionalDouble.empty())
                    .withRenderArea(new RenderPass.RenderArea(0, 0,
                        frame.variant.plan().shadowResolution(), frame.variant.plan().shadowResolution()))
            );
        }
        return createGbufferPass(encoder, label, color, clearColor, depth, clearDepth,
            frame.variant.terrainTargets(frame.group == ChunkSectionLayerGroup.TRANSLUCENT));
    }

    private static RenderPass createGbufferPass(CommandEncoder encoder, java.util.function.Supplier<String> label,
        GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth,
        java.util.List<Integer> logicalTargets) {
        FrameState frame = FRAME.get();
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(label);
        frame.pipeline.noteTerrainSubmission(currentProgramRoot(), frame.variant);
        // Auxiliary clearing is once per frame, before world draws, and honors Clear=false.
        for (int logicalTarget : logicalTargets) {
            if (logicalTarget == 0) descriptor.withColorAttachment(color, clearColor);
            else descriptor.withColorAttachment(frame.targets.colorView(logicalTarget),
                Optional.empty());
        }
        return encoder.createRenderPass(descriptor
            .withDepthAttachment(depth, clearDepth)
            .withRenderArea(new RenderPass.RenderArea(0, 0, color.getWidth(0), color.getHeight(0))));
    }

    public static void selectTerrainPipeline(RenderPass pass, RenderPipeline vanilla) {
        FrameState frame = FRAME.get();
        if (frame.mode == Mode.SHADOW && frame.variant != null && frame.variant.shadow() != null) {
            pass.setPipeline(frame.variant.shadow());
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesNoise()) frame.pipeline.bindNoise(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesSpecular()) frame.pipeline.bindSpecular(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesMaterialIds()) frame.pipeline.bindMaterials(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesWorldMatrices()) frame.pipeline.bindMatrices(pass);
        } else if (frame.mode == Mode.GBUFFER && frame.variant != null) {
            pass.setPipeline(frame.variant.terrain(vanilla));
            var nativeProgram=frame.variant.nativeTerrain(vanilla);
            if (nativeProgram != null && nativeProgram.usesOpaqueWorldDepth()) {
                if (frame.group != ChunkSectionLayerGroup.TRANSLUCENT)
                    throw new IllegalStateException("Opaque world depth may only be sampled by translucent terrain");
                pass.bindTexture("OculusOpaqueWorldDepth", frame.targets.opaqueWorldDepthView(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null) for (int input : nativeProgram.colorInputs()) {
                if (frame.variant.terrainTargets(vanilla).contains(input))
                    throw new IllegalStateException("Native terrain color feedback on colortex" + input);
                pass.bindTexture(dev.oculusforge.render.program.NativeColorSamplers.canonical(input),
                    frame.targets.colorView(input), RenderSystem.getSamplerCache().getClampToEdge(
                        com.mojang.blaze3d.textures.FilterMode.LINEAR));
            }
            if (nativeProgram != null && nativeProgram.usesShadowDepth()) {
                ShadowColorReadGuard.requireCompleted(frame.mode == Mode.SHADOW, frame.shadowRendered);
                pass.bindTexture("OculusShadowDepth", frame.targets.shadowDepthView(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null) for (int input : nativeProgram.shadowColorInputs()) {
                ShadowColorReadGuard.requireCompleted(frame.mode == Mode.SHADOW, frame.shadowRendered);
                pass.bindTexture(input == 0 ? "OculusShadowColor" : "OculusShadowColor1", frame.targets.shadowColorView(input),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null && nativeProgram.usesOpaqueShadowDepth()) {
                ShadowColorReadGuard.requireCompleted(false, frame.shadowRendered);
                pass.bindTexture("OculusOpaqueShadowDepth", frame.targets.shadowDepthView(1),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null && nativeProgram.usesNoise()) frame.pipeline.bindNoise(pass);
            if (nativeProgram != null && nativeProgram.usesSpecular()) frame.pipeline.bindSpecular(pass);
            if (nativeProgram != null && nativeProgram.usesMaterialIds()) frame.pipeline.bindMaterials(pass);
            if (nativeProgram != null && nativeProgram.usesWorldMatrices()) frame.pipeline.bindMatrices(pass);
        } else {
            pass.setPipeline(ExtendedTerrainMesh.pipeline(vanilla));
        }
    }

    public static GpuTexture currentColorTexture(int index) {
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline == null) throw new IllegalStateException("No active world renderer");
        return pipeline.targets().colorTexture(index);
    }

    public static void resetWorld() {
        RenderSystem.assertOnRenderThread();
        closeWorldCapture();
        FRAME.get().reset();
        PackShaderPipeline active = manager == null ? null : manager.activePipeline();
        if (active != null) active.resetTemporalHistory();
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline != null) pipeline.resetTargets();
    }

    public static boolean frameDeferred() { return FRAME.get().deferred; }

    public static GpuTextureView currentOpaqueWorldDepthView() {
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline == null) throw new IllegalStateException("No opaque world depth producer");
        return pipeline.targets().opaqueWorldDepthView();
    }

    public static GpuTextureView currentColorView(int index) {
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline == null) throw new IllegalStateException("No active world renderer");
        return pipeline.targets().colorView(index);
    }

    public static GpuTextureView currentShadowDepthView() {
        return currentShadowDepthView(0);
    }

    public static GpuTextureView currentShadowDepthView(int index) {
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline == null) throw new IllegalStateException("No active shadow renderer");
        return pipeline.targets().shadowDepthView(index);
    }

    public static GpuTextureView currentShadowColorView() {
        return currentShadowColorView(0);
    }

    public static GpuTextureView currentShadowColorView(int index) {
        WorldRenderPipeline pipeline = activeWorld();
        if (pipeline == null) throw new IllegalStateException("No active shadow renderer");
        return pipeline.targets().shadowColorView(index);
    }

    public static Matrix4f shadowProjection() {
        Matrix4f matrix = FRAME.get().shadowProjection;
        return matrix == null ? new Matrix4f() : new Matrix4f(matrix);
    }

    public static Matrix4f shadowModelView() {
        Matrix4f matrix = FRAME.get().shadowModelView;
        return matrix == null ? new Matrix4f() : new Matrix4f(matrix);
    }

    private static WorldRenderPipeline.Variant activeVariant() {
        WorldRenderPipeline pipeline = activeWorld();
        return pipeline == null ? null : pipeline.variant(currentProgramRoot());
    }

    private static WorldRenderPipeline activeWorld() {
        ShaderPipelineManager current = manager;
        PackShaderPipeline pipeline = current == null ? null : current.activePipeline();
        return pipeline == null ? null : pipeline.world();
    }

    private static String currentProgramRoot() {
        var level = Minecraft.getInstance().level;
        if (level == null) return "shaders";
        if (level.dimension().equals(Level.OVERWORLD)) return "shaders/world0";
        if (level.dimension().equals(Level.NETHER)) return "shaders/world-1";
        if (level.dimension().equals(Level.END)) return "shaders/world1";
        return "shaders";
    }

    private static void failWorld(String context, RuntimeException failure) {
        ShaderPipelineManager current = manager;
        OculusForge.LOGGER.error("{}; restoring vanilla rendering", context, failure);
        if (current != null) current.failRuntime(new IllegalStateException(context + ": " + failure.getMessage(), failure));
    }

    private enum Mode { OFF, GBUFFER, SHADOW }

    private static final class FrameState {
        private Mode mode = Mode.OFF;
        private int recursionDepth;
        private boolean shadowRendered;
        private boolean deferred;
        private boolean prepared;
        private ChunkSectionsToRender mainSections;
        private ChunkSectionsToRender shadowSections;
        private ChunkSectionLayerGroup group;
        private WorldRenderPipeline pipeline;
        private WorldRenderPipeline.Variant variant;
        private WorldRenderTargets targets;
        private Matrix4f shadowProjection;
        private Matrix4f shadowModelView;

        private void reset() {
            mode = Mode.OFF;
            recursionDepth = 0;
            shadowRendered = false;
            deferred = false;
            prepared = false;
            mainSections = null;
            shadowSections = null;
            group = null;
            pipeline = null;
            variant = null;
            targets = null;
            shadowProjection = null;
            shadowModelView = null;
        }
    }
}
