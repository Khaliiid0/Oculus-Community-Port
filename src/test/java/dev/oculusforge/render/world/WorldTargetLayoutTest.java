package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorldTargetLayoutTest {
    @Test void sparseHighIndexDoesNotAllocateTheInterveningTextures() {
        var layout = new WorldTargetLayout(1920, 1080, Set.of(0, 15), 0);
        assertEquals(Set.of(15), layout.auxiliaryBuffers());
        assertEquals(1, layout.textureCount());
        assertEquals(1920L * 1080 * 4, layout.byteSize());
    }

    @Test void sameNumberOfDifferentLogicalBuffersRequiresADifferentLayout() {
        assertNotEquals(new WorldTargetLayout(854, 480, Set.of(1, 4), 0),
            new WorldTargetLayout(854, 480, Set.of(2, 4), 0));
        assertEquals(new WorldTargetLayout(854, 480, Set.of(0, 4, 1), 0),
            new WorldTargetLayout(854, 480, Set.of(1, 4), 0));
    }

    @Test void shadowBudgetIncludesColorLiveDepthAndOpaqueDepthSnapshot() {
        var layout = new WorldTargetLayout(854, 480, Set.of(4, 6), 2048);
        assertEquals(5, layout.textureCount());
        assertEquals(854L * 480 * 8 + 2048L * 2048 * 12, layout.byteSize());
    }

    @Test void largestShadowLayoutLeavesNoBudgetForAuxiliaryColors() {
        assertEquals(WorldTargetLayout.MAX_BYTES,
            new WorldTargetLayout(854, 480, Set.of(), 4096).byteSize());
        assertThrows(IllegalStateException.class,
            () -> new WorldTargetLayout(854, 480, Set.of(1), 4096));
    }

    @Test void secondShadowColorHasItsOwnAllocationAndBudget() {
        var formats = dev.oculusforge.render.program.ColorBufferFormats.DEFAULT;
        var both = new WorldTargetLayout(854,480,Set.of(),2048,formats,false,Set.of(0,1));
        assertEquals(4,both.textureCount());
        assertEquals(2048L*2048*16,both.byteSize());
        assertNotEquals(both,new WorldTargetLayout(854,480,Set.of(),2048));
        assertNotEquals(new WorldTargetLayout(854,480,Set.of(),2048,formats,false,Set.of(1)),
            new WorldTargetLayout(854,480,Set.of(),2048));
        assertThrows(IllegalStateException.class,()->new WorldTargetLayout(854,480,Set.of(),4096,formats,false,Set.of(0,1)));
        assertThrows(IllegalArgumentException.class,()->new WorldTargetLayout(854,480,Set.of(),0,formats,false,Set.of(1)));
        assertThrows(IllegalArgumentException.class,()->new WorldTargetLayout(854,480,Set.of(),512,formats,false,Set.of()));
        assertThrows(IllegalArgumentException.class,()->new WorldTargetLayout(854,480,Set.of(),512,formats,false,Set.of(2)));
    }

    @Test void invalidAndOversizedLayoutsAreRejectedBeforeGpuAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new WorldTargetLayout(0, 480, Set.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTargetLayout(854, 480, Set.of(16), 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTargetLayout(854, 480, Set.of(-1), 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTargetLayout(854, 480, Set.of(1), 8192));
        assertThrows(IllegalStateException.class, () -> new WorldTargetLayout(8192, 8192, Set.of(1), 0));
        assertThrows(ArithmeticException.class,
            () -> new WorldTargetLayout(Integer.MAX_VALUE, Integer.MAX_VALUE, Set.of(1, 2), 0));
    }
}
