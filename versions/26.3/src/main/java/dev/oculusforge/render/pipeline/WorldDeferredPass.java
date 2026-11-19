package dev.oculusforge.render.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.*;
import dev.oculusforge.render.world.WorldRenderHooks;
import java.util.*;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Generation-owned scratch storage. Executed only while the main pass is suspended. */
public final class WorldDeferredPass implements AutoCloseable {
    private final WorldPostPhasePlan plan;
    private final DeferredScratchTargets scratch;
    private boolean logged, closed;

    public WorldDeferredPass(WorldPostPhasePlan plan) {
        this.plan=Objects.requireNonNull(plan);
        scratch=new DeferredScratchTargets(plan.deferred().scratchFormats(),new DeferredScratchTargets.Factory() {
            public GpuTexture texture(ColorBufferFormat format,int width,int height) {
                return RenderSystem.getDevice().createTexture(()->"Oculus deferred scratch",
                    GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING
                        |GpuTexture.USAGE_RENDER_ATTACHMENT,format.gpu(),width,height,1,1);
            }
            public GpuTextureView view(GpuTexture texture){return RenderSystem.getDevice().createTextureView(texture);}
        });
    }
    public int programCount() { return plan.deferredCount(); }
    public void checkMemoryBudget(int width, int height) { plan.checkMemoryBudget(width, height); }

    public void execute(PackShaderPipeline pipeline, PackShaderPipeline.Variant variant, LevelRenderState state,
                        RenderTarget main) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Deferred generation is closed");
        plan.checkMemoryBudget(main.width, main.height);
        scratch.ensure(main.width, main.height);
        var buffers = plan.deferred();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var borrowed = new HashMap<Integer, GpuTextureView>();
        for (var entry : buffers.borrowedColorFormats().entrySet()) {
            var view = WorldRenderHooks.currentColorView(entry.getKey());
            if (view.texture().getFormat() != entry.getValue().gpu()
                || view.getWidth(0) != main.width || view.getHeight(0) != main.height)
                throw new IllegalStateException("Deferred/world attachment mismatch");
            borrowed.put(entry.getKey(), view);
        }
        for (int index = 0; index < programCount(); index++)
            FullscreenProgramRunner.execute(encoder, variant.programs().get(index), buffers.steps().get(index),
                state, main, pipeline, variant, buffers,
                slot -> slot == ColorBufferPlan.MAIN ? main.getColorTextureView() : scratch.view(slot), borrowed);
        // Do not mutate any source attachment until every deferred draw succeeded.
        var copies=new ArrayList<DeferredTextureCopies.Copy>();
        // Stable logical order also keeps the main scene commit last.
        for(int logical=1;logical<ColorBufferPlan.MAX_BUFFERS;logical++) {
            var slot=buffers.retainedColorSlots().get(logical);
            if(slot!=null)copies.add(new DeferredTextureCopies.Copy(scratch.texture(slot),WorldRenderHooks.currentColorTexture(logical)));
        }
        if (buffers.presentedSlot() != ColorBufferPlan.MAIN)
            copies.add(new DeferredTextureCopies.Copy(scratch.texture(buffers.presentedSlot()),Objects.requireNonNull(main.getColorTexture())));
        new DeferredTextureCopies(copies,main.width,main.height).commit(encoder);
        if (!logged) {
            logged = true;
            OculusForge.LOGGER.info("Executed {} deferred stage(s) BEFORE translucent terrain; committed auxiliaries {}, scratch={} bytes",
                programCount(), buffers.retainedColorSlots().keySet(), buffers.scratchBytes(main.width, main.height));
        }
    }

    public void resetTargets() {
        RenderSystem.assertOnRenderThread();
        scratch.reset();
    }

    @Override public void close() {
        if (closed&&!scratch.hasOwnedResources()) return;
        closed = true;
        RenderSystem.assertOnRenderThread();scratch.close();
        OculusForge.LOGGER.info("Oculus deferred scratch retired: liveTextures={} liveBytes={}", DeferredScratchTargets.liveCount(), DeferredScratchTargets.liveBytes());
    }
}
