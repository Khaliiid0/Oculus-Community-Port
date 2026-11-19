package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.program.BlockMaterialMap.RenderLayer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BlockLayerRoutingTest {
    @Test void publishesImmutableTablesAndInvalidatesOnlyForEffectiveChanges() {
        var routing = new BlockLayerRouting<String>();
        var rebuilds = new AtomicInteger();
        var source = new HashMap<>(Map.of("leaf", RenderLayer.TRANSLUCENT));
        assertTrue(routing.publish(source, rebuilds::incrementAndGet));
        source.clear();
        assertFalse(routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), rebuilds::incrementAndGet));
        assertEquals(1, rebuilds.get());
        routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer()); return null; });
        assertTrue(routing.publish(Map.of(), rebuilds::incrementAndGet));
        assertFalse(routing.publish(Map.of(), rebuilds::incrementAndGet));
        assertEquals(2, rebuilds.get());
        routing.compile(() -> { routing.enterBlock("leaf"); assertNull(routing.forcedLayer()); return null; });
    }

    @Test void nestedBlocksAndUnmappedFluidOutputCannotInheritTheLastBlockOverride() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        routing.compile(() -> {
            var before = routing.enterBlock("leaf");
            try {
                assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer());
                var nested = routing.enterBlock("unmapped");
                try { assertNull(routing.forcedLayer()); } finally { routing.restoreBlock(nested); }
                assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer());
            } finally { routing.restoreBlock(before); }
            assertNull(routing.forcedLayer(), "Fluid callback outside a model must retain its vanilla layer");
            return null;
        });
        assertNull(routing.forcedLayer());
    }

    @Test void compileExceptionsRemoveThreadStateAndLaterSectionsUseTheNewTable() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        assertThrows(IllegalStateException.class, () -> routing.compile(() -> {
            routing.enterBlock("leaf"); throw new IllegalStateException("tessellation failed");
        }));
        assertNull(routing.forcedLayer());
        routing.publish(Map.of("leaf", RenderLayer.SOLID), () -> {});
        routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.SOLID, routing.forcedLayer()); return null; });
    }

    @Test void nestedCompilationRestoresTheOuterSnapshotAndCurrentBlock() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        routing.compile(() -> {
            routing.enterBlock("leaf");
            routing.publish(Map.of("leaf", RenderLayer.CUTOUT), () -> {});
            routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.CUTOUT, routing.forcedLayer()); return null; });
            assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer());
            routing.enterBlock("leaf");
            assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer());
            return null;
        });
        assertNull(routing.forcedLayer());
    }

    @Test void switchingPacksCannotChangeAnAlreadyRunningWorkerSection() throws Exception {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        var entered = new CountDownLatch(1);
        var switched = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var task = executor.submit(() -> routing.compile(() -> {
                entered.countDown();
                try { assertTrue(switched.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                routing.enterBlock("leaf");
                return routing.forcedLayer();
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            routing.publish(Map.of("leaf", RenderLayer.SOLID), () -> {});
            assertNull(routing.forcedLayer());
            switched.countDown();
            assertEquals(RenderLayer.TRANSLUCENT, task.get(5, TimeUnit.SECONDS));
        } finally { switched.countDown(); }
        routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.SOLID, routing.forcedLayer()); return null; });
    }

    @Test void failedInvalidationRestoresThePreviousTableAndSchedulesItsRebuild() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        var calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> routing.publish(Map.of(), () -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("invalidation failed");
        }));
        assertEquals(2, calls.get());
        routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.TRANSLUCENT, routing.forcedLayer()); return null; });
    }

    @Test void failedRollbackInvalidationIsSuppressedWithoutLosingThePreviousTable() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.CUTOUT), () -> {});
        var error = assertThrows(IllegalStateException.class, () -> routing.publish(Map.of(), () -> { throw new IllegalStateException("failed"); }));
        assertEquals(1, error.getSuppressed().length);
        routing.compile(() -> { routing.enterBlock("leaf"); assertEquals(RenderLayer.CUTOUT, routing.forcedLayer()); return null; });
    }

    @Test void inputOutsideACompilationCannotInstallThreadLocalOverrides() {
        var routing = new BlockLayerRouting<String>();
        routing.publish(Map.of("leaf", RenderLayer.TRANSLUCENT), () -> {});
        assertNull(routing.enterBlock("leaf"));
        routing.restoreBlock(RenderLayer.CUTOUT);
        assertNull(routing.forcedLayer());
    }

    @Test void identicalRollbackExceptionDoesNotMaskTheOriginalFailure() {
        var routing = new BlockLayerRouting<String>();
        var failure = new IllegalStateException("same exception instance");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> routing.publish(
            Map.of("leaf", RenderLayer.CUTOUT), () -> { throw failure; })));
        routing.compile(() -> { routing.enterBlock("leaf"); assertNull(routing.forcedLayer()); return null; });
    }
}
