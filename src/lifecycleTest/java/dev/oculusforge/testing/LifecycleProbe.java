package dev.oculusforge.testing;

import dev.oculusforge.OculusForge;
import dev.oculusforge.client.OculusClientService;
import dev.oculusforge.client.gui.ShaderPackScreen;
import dev.oculusforge.render.program.PackGpuTexture;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development source set only. Never included in a distributable binary/sources JAR. */
@Mod.EventBusSubscriber(modid = "oculus", value = Dist.CLIENT)
public final class LifecycleProbe {
    private static final int ORDINARY_SWITCHES = ordinarySwitches();
    private static final int TOTAL_CHECKS = ORDINARY_SWITCHES + 8;
    private static int step;
    private static int settleTicks;
    private static boolean started;
    private static boolean finished;
    private static long startedAt;
    private static CompletableFuture<Void> pending;
    private static String expected;
    private static boolean expectedFailure;

    private LifecycleProbe() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (finished || minecraft.level == null) return;
        OculusClientService service = ((OculusForge) ModList.getModContainerById("oculus").orElseThrow().getMod()).clientService();
        try {
            if (!started) {
                started = true; startedAt = System.nanoTime(); settleTicks = 30;
                OculusForge.LOGGER.info("OCULUS_LIFECYCLE_START: {} ordinary switches plus failure/full-reload/overlap/burst checks",
                    ORDINARY_SWITCHES);
            }
            long timeout = Math.max(180_000_000_000L, TOTAL_CHECKS * 750_000_000L);
            if (System.nanoTime() - startedAt > timeout) throw new IllegalStateException("Lifecycle probe timed out at step " + step);
            if (pending != null) {
                if (!pending.isDone()) return;
                pending.join();
                if (settleTicks-- > 0) return; // Let the renderer execute several actual frames.
                if (!service.activePackId().equals(expected)) throw new IllegalStateException("Wrong active pack at step " + step + ": " + service.activePackId());
                if (service.shadersActive() != !expected.isEmpty()) throw new IllegalStateException("Wrong enabled state at step " + step);
                if (service.pipelineError().isBlank() == expectedFailure) throw new IllegalStateException("Wrong failure state at step " + step + ": " + service.pipelineError());
                int textures = expected.equals("noise-preview") || expected.equals("custom-texture-roundtrip") ? 1 : 0;
                long bytes = expected.equals("noise-preview") ? 65536 : expected.equals("custom-texture-roundtrip") ? 4 : 0;
                if (PackGpuTexture.liveCount() != textures || PackGpuTexture.liveBytes() != bytes) {
                    throw new IllegalStateException("Owned texture leak/wrong allocation at step " + step + ": "
                        + PackGpuTexture.liveCount() + "/" + PackGpuTexture.liveBytes());
                }
                int uniformRings = expected.equals("custom-texture-roundtrip") ? 1 : 0;
                if (dev.oculusforge.render.program.PackFinalProgram.liveUniformRings() != uniformRings) {
                    throw new IllegalStateException("Uniform ring leak at step " + step);
                }
                if (service.cachedPipelineCount() > 10 || (step >= 3 && step < ORDINARY_SWITCHES && service.cachedPipelineCount() != 7)) {
                    throw new IllegalStateException("Pipeline descriptor cache did not plateau: " + service.cachedPipelineCount());
                }
                OculusForge.LOGGER.info("OCULUS_LIFECYCLE_CHECK step={} active='{}' ownedTextures={} ownedBytes={} uniformRings={} cachedPipelines={}",
                    step, expected, textures, bytes, uniformRings, service.cachedPipelineCount());
                pending = null; step++;
            } else if (settleTicks-- > 0) return;
            if (step >= TOTAL_CHECKS) {
                finished = true;
                OculusForge.LOGGER.info("OCULUS_LIFECYCLE_PASS checks={} elapsedMs={}\n{}", step,
                    (System.nanoTime() - startedAt) / 1_000_000, service.diagnosticSummary());
                minecraft.gui.setScreen(new ShaderPackScreen(null, service));
                return;
            }
            expectedFailure = false;
            if (step < ORDINARY_SWITCHES) {
                expected = switch (step % 4) {
                    case 0 -> "noise-preview";
                    case 1 -> "custom-texture-roundtrip";
                    case 2 -> "";
                    default -> "buffer-roundtrip";
                };
                pending = service.applySelection(expected, !expected.isEmpty());
            } else {
                switch (step - ORDINARY_SWITCHES) {
                    case 0 -> {
                        expected = "buffer-roundtrip"; expectedFailure = true;
                        pending = service.applySelection("unsupported-probe", true);
                    }
                    case 1 -> {
                        expected = "buffer-roundtrip"; expectedFailure = true;
                        pending = service.applySelection("compile-error-probe", true);
                    }
                    case 2, 7 -> { expected = "custom-texture-roundtrip"; pending = service.applySelection(expected, true); }
                    case 3 -> { expected = "custom-texture-roundtrip"; pending = minecraft.reloadResourcePacks(); }
                    case 4 -> {
                        expected = "noise-preview";
                        var full = minecraft.reloadResourcePacks();
                        pending = CompletableFuture.allOf(full, service.applySelection(expected, true));
                    }
                    case 5 -> {
                        var futures = new ArrayList<CompletableFuture<Void>>();
                        for (int index = 0; index < 12; index++) {
                            String pack = switch (index % 3) {
                                case 0 -> "noise-preview"; case 1 -> "buffer-roundtrip"; default -> "custom-texture-roundtrip";
                            };
                            futures.add(service.applySelection(pack, true));
                        }
                        expected = "custom-texture-roundtrip";
                        pending = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
                    }
                    case 6 -> { expected = ""; pending = service.applySelection("", false); }
                    default -> throw new IllegalStateException("Unexpected probe step");
                }
            }
            settleTicks = 5;
        } catch (RuntimeException failure) {
            finished = true;
            OculusForge.LOGGER.error("OCULUS_LIFECYCLE_FAIL step=" + step, failure);
            service.applySelection("", false);
        }
    }

    private static int ordinarySwitches() {
        int switches = Integer.getInteger("oculus.lifecycleSwitches", 60);
        if (switches < 60 || switches > 2000 || switches % 4 != 0) {
            throw new IllegalArgumentException("oculus.lifecycleSwitches must be a multiple of four from 60 through 2000");
        }
        return switches;
    }
}
