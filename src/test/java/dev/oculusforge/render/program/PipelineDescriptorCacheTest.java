package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PipelineDescriptorCacheTest {
    @Test void identicalSourcesReuseTheExactSameObjectAcrossAThousandSwitches() throws Exception {
        var cache = new PipelineDescriptorCache<Object>();
        var first = cache.getOrCreate("source", Object::new);
        for (int i = 0; i < 1000; i++) assertSame(first, cache.getOrCreate("source", () -> fail("Must not rebuild descriptor")));
        assertEquals(1, cache.size());
    }

    @Test void distinctSourcesNeverAlias() throws Exception {
        var cache = new PipelineDescriptorCache<Object>();
        assertNotSame(cache.getOrCreate("a", Object::new), cache.getOrCreate("b", Object::new));
    }

    @Test void capacityFailurePreservesExistingObjectsInsteadOfEvictingAndLeakingGpuKeys() throws Exception {
        var cache = new PipelineDescriptorCache<Object>(2);
        var a = cache.getOrCreate("a", Object::new); cache.getOrCreate("b", Object::new);
        assertThrows(ShaderCompatibilityException.class, () -> cache.getOrCreate("c", Object::new));
        assertSame(a, cache.getOrCreate("a", Object::new)); assertEquals(2, cache.size());
    }

    @Test void wholePackCapacityCanBeCheckedBeforeAllocatingAnything() throws Exception {
        var cache = new PipelineDescriptorCache<Object>(2);
        cache.getOrCreate("a", Object::new);
        assertDoesNotThrow(() -> cache.requireCapacity(Set.of("a", "b")));
        assertThrows(ShaderCompatibilityException.class, () -> cache.requireCapacity(Set.of("b", "c")));
        assertEquals(1, cache.size());
    }

    @Test void fullResourceResetAllowsNewDescriptors() throws Exception {
        var cache = new PipelineDescriptorCache<Object>(1);
        var old = cache.getOrCreate("a", Object::new); cache.clear();
        assertEquals(0, cache.size()); assertNotSame(old, cache.getOrCreate("a", Object::new));
    }

    @Test void failedFactoriesAreNotRetained() {
        var cache = new PipelineDescriptorCache<Object>();
        var calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.getOrCreate("a", () -> {
            calls.incrementAndGet(); throw new IllegalStateException("bad descriptor");
        }));
        assertEquals(1, calls.get()); assertEquals(0, cache.size());
    }
}
