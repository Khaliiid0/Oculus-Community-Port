package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
        refreshFrameCompatibility();
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

    private static void refreshFrameCompatibility() {
        ShaderPipelineManager current = manager;
        if (current == null) return;
        var configured = current.configuredPipeline();
        var variant = configured == null ? null : configured.variant(currentProgramRoot());
        boolean needsClassic = variant != null && (variant.centerDepth() != null
            || configured.world() != null && configured.world().variant(currentProgramRoot()) != null);
        var renderer = Minecraft.getInstance().gameRenderer;
        try {
            current.updateFrameCompatibility(needsClassic,
                renderer.gameRenderState().optionsRenderState.improvedTransparency,
                renderer.useImprovedTransparency());
        } catch (RuntimeException failure) {
            // Routing/resource failures are still real failures, never auto-resumed.
            failWorld("Could not update rendering compatibility", failure);
        }
    }
    public static void failSceneCapture(RuntimeException failure) { failWorld("HDR scene capture failed",failure); }
    public static void endSceneCapture() {
        try { WorldSceneCapture.end(); }
        catch(RuntimeException failure) { failSceneCapture(failure); }
    }

    public static void selectEndSkyPipeline(RenderPass pass, CompiledRenderPipeline vanilla) {
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
        frame.levelState = state;
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
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            frame.deferred = true;
            failWorld("World renderer does not yet support 26.3 improved transparency",
                new IllegalStateException("Use classic transparency for the experimental world renderer"));
            return;
        }
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
                manager.activePipeline().variant(currentProgramRoot()).cameraHistory(),
                manager.activePipeline().variant(currentProgramRoot()).wetness())) {
                // Do not bind stale uniforms or run post-processing on missing world outputs.
                // Retry next frame; this transient camera state is not a broken shader pack.
                frame.deferred = true;
                return;
            }
            pipeline.ensureTargets(main.width, main.height, variant).beginFrame();
            frame.prepared = true;
            if (variant.shadow() == null) return;
            frame.shadowSections = mainSections instanceof ChunkSectionsToRender.DrawIndirect
                ? renderer.prepareChunkRendersIndirect(shadowMatrix, true)
                : renderer.prepareChunkRenders(shadowMatrix, true);
        } catch (RuntimeException failure) {
            failWorld("Could not prepare world rendering", failure);
            frame.reset();
        }
    }

    private static void beginTerrainGroup(ChunkSectionsToRender sections, ChunkSectionLayerGroup group, GpuSampler sampler, GpuTextureView atlas) {
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
                            case OPAQUE -> renderShadowGroup(frame.shadowSections, ChunkSectionLayerGroup.OPAQUE, sampler, atlas);
                            case SNAPSHOT -> targets.snapshotOpaqueShadowDepth();
                            case TRANSLUCENT -> renderShadowGroup(frame.shadowSections, ChunkSectionLayerGroup.TRANSLUCENT, sampler, atlas);
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

        } catch (RuntimeException failure) {
            frame.mode = Mode.OFF;
            throw failure; // Outer routing closes every pass before retiring the failed generation.
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

    /** Wrap only the pinned main pass; shader-off retains the untouched vanilla pass. */
    private static dev.oculusforge.render.pipeline.CenterDepthPass activeCenterDepth() {
        var pipeline=manager==null?null:manager.activePipeline();
        var variant=pipeline==null?null:pipeline.variant(currentProgramRoot());
        return variant==null?null:variant.centerDepth();
    }
    public static RenderPass createMainPass(CommandEncoder encoder, java.util.function.Supplier<String> label,
        GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth) {
        var center=activeCenterDepth();
        if(center!=null&&Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            failWorld("Center-depth sampling requires classic transparency",new IllegalStateException("Improved transparency is not qualified"));
            return encoder.createRenderPass(label,color,clearColor,depth,clearDepth);
        }
        if (FRAME.get().deferred || activeWorld()==null&&center==null)
            return encoder.createRenderPass(label,color,clearColor,depth,clearDepth);
        if (clearColor.isPresent() || clearDepth.isPresent())
            throw new IllegalStateException("Restartable main pass must preserve existing color/depth");
        return new dev.oculusforge.port.RestartableRenderPass(
            () -> encoder.createRenderPass(label,color,Optional.empty(),depth,OptionalDouble.empty()));
    }

    public static boolean terrainActive() { return FRAME.get().mode != Mode.OFF; }

    public static void captureOpaqueDepthBeforeTransparency(RenderPass mainPass) {
        var frame = FRAME.get();
        var owner = activeWorld();
        var variant = owner == null ? null : owner.variant(currentProgramRoot());
        var center=activeCenterDepth();
        boolean worldReady=frame.prepared&&!frame.deferred&&variant!=null;
        if(!worldReady&&(center==null||frame.deferred||variant!=null))return;
        if (frame.deferredCompleted) throw new IllegalStateException("Opaque/deferred boundary repeated in one frame");
        if (!(mainPass instanceof dev.oculusforge.port.RestartableRenderPass restartable))
            throw new IllegalStateException("Opaque snapshot needs a restartable main pass");
        try {
            restartable.runOutside(() -> {
                var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                if(center!=null)center.capture(RenderSystem.getDevice().createCommandEncoder(),main.getDepthTextureView(),
                    Minecraft.getInstance().level,dev.oculusforge.render.program.RenderAnimationClock.current());
                if(!worldReady)return; // Post-only packs still sample real pre-translucent depth.
                if (owner.needsOpaqueWorldDepth(variant)) owner.targets().snapshotOpaqueWorldDepth(main.getDepthTexture());
                var pipeline = manager.activePipeline();
                var post = pipeline.variant(currentProgramRoot());
                if (post.deferred() != null) {
                    captureWorldStage("02b-before-deferred");
                    post.deferred().execute(pipeline, post, frame.levelState, main);
                    captureWorldStage("02c-after-deferred");
                }
                frame.deferredCompleted = true;
            });
        } catch (RuntimeException failure) {
            // The restartable pass is restored before retiring its generation.
            com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL33C.GL_TEXTURE0);
            frame.deferred = true;
            failWorld("Opaque/deferred handoff failed", failure);
            if(!restartable.isUsable())throw failure; // Never continue vanilla commands on a failed main-pass restore.
        }
    }

    public static boolean deferredCompleted() { return FRAME.get().deferredCompleted; }

    /** Suspend the main pass, render per-layer MRTs, then restore main feature state. */
    public static void renderTerrainGroup(ChunkSectionsToRender sections, ChunkSectionLayerGroup group,
        RenderPass mainPass, GpuSampler sampler, GpuTextureView atlas, boolean wireframe) {
        if (activeWorld() == null || !FRAME.get().prepared || FRAME.get().deferred) {
            sections.renderGroup(group,mainPass,sampler,atlas,wireframe);
            return;
        }
        if (!(mainPass instanceof dev.oculusforge.port.RestartableRenderPass restartable))
            throw new IllegalStateException("Native terrain cannot create a pass inside the main pass");
        if (wireframe) {
            failWorld("Native terrain wireframe is not qualified",new IllegalStateException("Wireframe requested"));
            sections.renderGroup(group,mainPass,sampler,atlas,true);
            return;
        }
        try {
            restartable.runOutside(() -> {
                try {
                    beginTerrainGroup(sections,group,sampler,atlas);
                    if (FRAME.get().pipeline == null) throw new IllegalStateException("Missing terrain frame owner");
                    var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                    boolean indirect = sections instanceof ChunkSectionsToRender.DrawIndirect;
                    for (var layer : group.layers()) {
                        var descriptor = layer.pipeline(indirect);
                        var encoder = RenderSystem.getDevice().createCommandEncoder();
                        try (RenderPass pass = FRAME.get().mode == Mode.GBUFFER
                            ? createGbufferPass(encoder,() -> "Oculus terrain " + layer,
                                main.getColorTextureView(),Optional.empty(),main.getDepthTextureView(),OptionalDouble.empty(),
                                FRAME.get().variant.terrainTargets(descriptor))
                            : encoder.createRenderPass(() -> "Oculus vanilla terrain",main.getColorTextureView(),
                                Optional.empty(),main.getDepthTextureView(),OptionalDouble.empty())) {
                            RenderSystem.bindDefaultUniforms(pass);
                            renderLayers(sections,new net.minecraft.client.renderer.chunk.ChunkSectionLayer[]{layer},
                                sampler,pass,atlas);
                        }
                    }
                } finally { endTerrainGroup(); }
            });
        } catch (RuntimeException failure) {
            failWorld("26.3 terrain submission failed",failure);
            // Never replay partially submitted terrain in this frame.
            if (!restartable.isUsable()) throw failure;
        }
    }

    private static void renderLayers(ChunkSectionsToRender sections,
        net.minecraft.client.renderer.chunk.ChunkSectionLayer[] layers, GpuSampler sampler,
        RenderPass pass, GpuTextureView atlas) {
        ((dev.oculusforge.mixin.ChunkSectionsToRenderMixin) sections).oculus$renderLayers(
            layers,sampler,pass,atlas,Minecraft.getInstance().gameRenderer.lightmap(),null,null);
    }

    private static void renderShadowGroup(ChunkSectionsToRender sections, ChunkSectionLayerGroup group,
        GpuSampler sampler, GpuTextureView atlas) {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = createTerrainPass(RenderSystem.getDevice().createCommandEncoder(),
            () -> "Oculus shadow " + group,main.getColorTextureView(),Optional.empty(),
            main.getDepthTextureView(),OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            renderLayers(sections,group.layers(),sampler,pass,atlas);
        }
    }

    public static RenderPass createTerrainPass(CommandEncoder encoder, java.util.function.Supplier<String> label,
                                               GpuTextureView color, Optional<Vector4fc> clearColor,
                                               GpuTextureView depth, OptionalDouble clearDepth) {
        FrameState frame = FRAME.get();
        if (frame.mode == Mode.OFF) return encoder.createRenderPass(label, color, clearColor, depth, clearDepth);
        if (frame.mode == Mode.SHADOW) {
            frame.pipeline.noteShadowSubmission(currentProgramRoot(), frame.variant);
            var descriptor = RenderPassDescriptor.builder(() -> "Oculus terrain shadow");
            // Fragment locations follow the pack's declared order, not sorted logical IDs.
            // beginFrame clears once; later passes must preserve earlier shadow geometry.
            for (int target : frame.variant.plan().shadowTargets())
                descriptor.withColorAttachment(frame.targets.shadowColorView(target), Optional.empty());
            return encoder.createRenderPass(descriptor
                    .withDepthAttachment(frame.targets.shadowDepthView(), OptionalDouble.empty())
                    .withRenderArea(new RenderPass.RenderArea(0, 0,
                        frame.variant.plan().shadowResolution(), frame.variant.plan().shadowResolution())).build()
            );
        }
        return createGbufferPass(encoder, label, color, clearColor, depth, clearDepth,
            frame.variant.terrainTargets(frame.group == ChunkSectionLayerGroup.TRANSLUCENT));
    }

    private static RenderPass createGbufferPass(CommandEncoder encoder, java.util.function.Supplier<String> label,
        GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth,
        java.util.List<Integer> logicalTargets) {
        FrameState frame = FRAME.get();
        RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(label);
        frame.pipeline.noteTerrainSubmission(currentProgramRoot(), frame.variant);
        // Auxiliary clearing is once per frame, before world draws, and honors Clear=false.
        for (int logicalTarget : logicalTargets) {
            if (logicalTarget == 0) descriptor.withColorAttachment(color, clearColor);
            else descriptor.withColorAttachment(frame.targets.colorView(logicalTarget),
                Optional.empty());
        }
        return encoder.createRenderPass(descriptor
            .withDepthAttachment(depth, clearDepth)
            .withRenderArea(new RenderPass.RenderArea(0, 0, color.getWidth(0), color.getHeight(0))).build());
    }

    public static void selectTerrainPipeline(RenderPass pass, RenderPipeline vanilla, boolean indirect) {
        FrameState frame = FRAME.get();
        var backendVariant = frame.pipeline == null ? null : frame.pipeline.variant(currentProgramRoot(),indirect);
        if (frame.mode != Mode.OFF && backendVariant == null)
            throw new IllegalStateException("Missing compiled terrain backend family");
        if (frame.mode == Mode.SHADOW && frame.variant != null && frame.variant.shadow() != null) {
            pass.setPipeline(frame.pipeline.compiled(backendVariant.shadow()));
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesNoise()) frame.pipeline.bindNoise(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesSpecular()) frame.pipeline.bindSpecular(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesNormals()) frame.pipeline.bindNormals(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesMaterialIds()) frame.pipeline.bindMaterials(pass);
            if (frame.variant.plan().nativeShadow() != null && frame.variant.plan().nativeShadow().usesWorldMatrices()) frame.pipeline.bindMatrices(pass);
        } else if (frame.mode == Mode.GBUFFER && frame.variant != null) {
            pass.setPipeline(frame.pipeline.compiled(backendVariant.terrain(vanilla)));
            var nativeProgram=frame.variant.nativeTerrain(vanilla);
            if (nativeProgram != null && nativeProgram.usesOpaqueWorldDepth()) {
                if (frame.group != ChunkSectionLayerGroup.TRANSLUCENT)
                    throw new IllegalStateException("Opaque world depth may only be sampled by translucent terrain");
                pass.setUniform("OculusOpaqueWorldDepth", frame.targets.opaqueWorldDepthView(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null) for (int input : nativeProgram.colorInputs()) {
                if (frame.variant.terrainTargets(vanilla).contains(input))
                    throw new IllegalStateException("Native terrain color feedback on colortex" + input);
                pass.setUniform(dev.oculusforge.render.program.NativeColorSamplers.canonical(input),
                    frame.targets.colorView(input), RenderSystem.getSamplerCache().getClampToEdge(
                        com.mojang.renderpearl.api.textures.FilterMode.LINEAR));
            }
            if (nativeProgram != null && nativeProgram.usesShadowDepth()) {
                ShadowColorReadGuard.requireCompleted(frame.mode == Mode.SHADOW, frame.shadowRendered);
                pass.setUniform("OculusShadowDepth", frame.targets.shadowDepthView(),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null) for (int input : nativeProgram.shadowColorInputs()) {
                ShadowColorReadGuard.requireCompleted(frame.mode == Mode.SHADOW, frame.shadowRendered);
                pass.setUniform(input == 0 ? "OculusShadowColor" : "OculusShadowColor1", frame.targets.shadowColorView(input),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null && nativeProgram.usesOpaqueShadowDepth()) {
                ShadowColorReadGuard.requireCompleted(false, frame.shadowRendered);
                pass.setUniform("OculusOpaqueShadowDepth", frame.targets.shadowDepthView(1),
                    RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST));
            }
            if (nativeProgram != null && nativeProgram.usesNoise()) frame.pipeline.bindNoise(pass);
            if (nativeProgram != null && nativeProgram.usesSpecular()) frame.pipeline.bindSpecular(pass);
            if (nativeProgram != null && nativeProgram.usesNormals()) frame.pipeline.bindNormals(pass);
            if (nativeProgram != null && nativeProgram.usesMaterialIds()) frame.pipeline.bindMaterials(pass);
            if (nativeProgram != null && nativeProgram.usesWorldMatrices()) frame.pipeline.bindMatrices(pass);
        } else {
            pass.setPipeline(RenderSystem.getCompiledPipeline(ExtendedTerrainMesh.pipeline(vanilla)));
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
        private boolean deferredCompleted;
        private net.minecraft.client.renderer.state.level.LevelRenderState levelState;
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
            deferredCompleted = false;
            levelState = null;
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
