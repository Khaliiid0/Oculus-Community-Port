package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class MaterialVertexContextTest {
    @Test void identityHasUnknownSentinelAndSeparateFluidType() {
        assertEquals(0, MaterialVertexContext.encode(-1, false));
        assertEquals(0, MaterialVertexContext.encode(-1, true));
        assertEquals(1, MaterialVertexContext.encode(0, false));
        assertEquals(-1, MaterialVertexContext.encode(0, true));
        assertEquals(MaterialIdTable.MAX_STATES - 1, MaterialVertexContext.encode(MaterialIdTable.MAX_STATES - 2, false));
        assertThrows(IllegalArgumentException.class, () -> MaterialVertexContext.encode(MaterialIdTable.MAX_STATES - 1, false));
    }

    @Test void nestedFailureUnwindsWithoutLeakingStateToAnotherWorker() {
        assertEquals(0, MaterialVertexContext.current());
        int outer = MaterialVertexContext.enter(42);
        try {
            assertEquals(0, CompletableFuture.supplyAsync(MaterialVertexContext::current).join());
            assertThrows(IllegalStateException.class, () -> {
                int nested = MaterialVertexContext.enter(-17);
                try { assertEquals(-17, MaterialVertexContext.current()); throw new IllegalStateException("probe"); }
                finally { MaterialVertexContext.restore(nested); }
            });
            assertEquals(42, MaterialVertexContext.current());
        } finally { MaterialVertexContext.restore(outer); }
        assertEquals(0, MaterialVertexContext.current());
    }
}
