package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompiledPipelineRetargeterTest {
    static final class Backend implements BackendRenderPipeline {
        int closes;
        public boolean isClosed() { return closes != 0; }
        public void close() { closes++; }
    }
    private ColorTargetState color(GpuFormat format, int mask) {
        return new ColorTargetState(Optional.empty(),format,mask);
    }
    private FrontendRenderPipeline compiled(ColorTargetState... states) {
        return new FrontendRenderPipeline("test",new Backend(),List.of(),Object2IntMaps.emptyMap(),List.of(),
            Arrays.asList(states),false,0);
    }
    private RenderPipeline descriptor(String name) {
        return RenderPipeline.builder().withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus",name)).withVertexShader("test")
            .withFragmentShader("test").withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT).build();
    }
    private void alive(FrontendRenderPipeline... pipelines) {
        for (var pipeline:pipelines) assertEquals(0,((Backend)pipeline.backendRenderPipeline()).closes);
    }

    @Test void unrelatedAttachmentsDoNotInspectOrCompileAnything() {
        var links=new CompiledPipelineRetargeter(2);
        assertNull(links.retarget(null,GpuFormat.RGBA16_FLOAT,15,0,
            d->{fail("unrelated adaptation");return null;},d->{fail("unrelated compile");return null;}));
    }
    @Test void alreadyCorrectNativePipelineNeedsNoDescriptorOrNewCompile() {
        var links=new CompiledPipelineRetargeter(2);var original=compiled(color(GpuFormat.RGBA16_FLOAT,7));
        assertSame(original,links.retarget(original,GpuFormat.RGBA16_FLOAT,7,1,
            d->{fail("already correct");return null;},d->{fail("already correct");return null;}));
        alive(original);
    }
    @Test void retargetUsesOnlyTheExactRegisteredDescriptorAndBorrowedResult() {
        var links=new CompiledPipelineRetargeter(4);var source=descriptor("source");var adapted=descriptor("adapted");
        var original=compiled(color(GpuFormat.RGBA8_UNORM,15));var result=compiled(color(GpuFormat.RGBA16_FLOAT,15));
        assertTrue(links.remember(source,original));assertTrue(links.remember(source,original));
        assertSame(result,links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,
            d->{assertSame(source,d);return adapted;},d->{assertSame(adapted,d);return result;}));
        links.clear();assertEquals(0,links.size());alive(original,result);
    }
    @Test void aliasingDescriptorsBecomeAmbiguousUntilReset() {
        var links=new CompiledPipelineRetargeter(4);var a=descriptor("a");var b=descriptor("b");
        var original=compiled(color(GpuFormat.RGBA8_UNORM,15));
        assertTrue(links.remember(a,original));assertFalse(links.remember(b,original));assertFalse(links.remember(a,original));
        assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->null));
        links.clear();assertTrue(links.remember(b,original));alive(original);
    }
    @Test void budgetOverflowDoesNotBreakVanillaLookupOrGrowTheCache() {
        var links=new CompiledPipelineRetargeter(1);var source=descriptor("source");
        var known=compiled(color(GpuFormat.RGBA8_UNORM,15));var unknown=compiled(color(GpuFormat.RGBA8_UNORM,15));
        assertTrue(links.remember(source,known));assertFalse(links.remember(source,unknown));assertEquals(1,links.size());
        assertThrows(IllegalStateException.class,()->links.retarget(unknown,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->null));
        alive(known,unknown);
    }
    @Test void unknownAndClosedPipelinesRejectBeforeRecompiling() {
        var links=new CompiledPipelineRetargeter(2);var original=compiled(color(GpuFormat.RGBA8_UNORM,15));
        assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->null));
        original.close();assertFalse(links.remember(descriptor("closed"),original));
        assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->null));
        assertEquals(1,((Backend)original.backendRenderPipeline()).closes);
    }
    @Test void maskValidationRejectsNonexistentAndUnusedSlots() {
        var links=new CompiledPipelineRetargeter(2);var original=compiled(color(GpuFormat.RGBA8_UNORM,15),null);
        for(int mask:new int[]{-1,4}) assertThrows(IllegalArgumentException.class,
            ()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,mask,d->d,d->null));
        assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,2,d->d,d->null));
        assertThrows(IllegalArgumentException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,16,1,d->d,d->null));
    }
    @Test void paddedRgbRetargetsTheSceneSlotWithoutTouchingAuxiliaryData() {
        var links=new CompiledPipelineRetargeter(2);var auxiliary=color(GpuFormat.RGBA32_FLOAT,15);
        var original=compiled(auxiliary,color(GpuFormat.RGBA8_UNORM,15));
        var result=compiled(auxiliary,color(GpuFormat.RGBA16_FLOAT,7));
        links.remember(descriptor("mrt"),original);
        assertSame(result,links.retarget(original,GpuFormat.RGBA16_FLOAT,7,2,d->d,d->result));alive(original,result);
    }
    @Test void wronglyCompiledFormatOrAlphaMaskIsRejectedWithoutDisposingBorrowedGpuObjects() {
        var links=new CompiledPipelineRetargeter(2);var original=compiled(color(GpuFormat.RGBA8_UNORM,15));
        links.remember(descriptor("source"),original);
        for(var bad:List.of(compiled(color(GpuFormat.RGBA8_UNORM,7)),compiled(color(GpuFormat.RGBA16_FLOAT,15)),
            compiled(color(GpuFormat.RGBA16_FLOAT,3)))) {
            assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,7,1,d->d,d->bad));
            alive(bad);
        }
        alive(original);
    }
    @Test void alteredUnownedAttachmentsOrSceneBlendAreRejected() {
        var links=new CompiledPipelineRetargeter(2);var aux=color(GpuFormat.RGBA32_FLOAT,15);
        var original=compiled(color(GpuFormat.RGBA8_UNORM,15),aux);links.remember(descriptor("source"),original);
        var wrongAux=compiled(color(GpuFormat.RGBA16_FLOAT,15),color(GpuFormat.RGBA8_UNORM,15));
        var wrongBlend=compiled(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),GpuFormat.RGBA16_FLOAT,15),aux);
        for(var bad:List.of(wrongAux,wrongBlend)) {
            assertThrows(IllegalStateException.class,()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->bad));
            alive(bad);
        }
    }
    @Test void compilerFailureRemainsTheOriginalFailureAndNeverClosesVanillaPipeline() {
        var links=new CompiledPipelineRetargeter(2);var original=compiled(color(GpuFormat.RGBA8_UNORM,15));
        links.remember(descriptor("source"),original);var failure=new IllegalStateException("compiler");
        assertSame(failure,assertThrows(IllegalStateException.class,
            ()->links.retarget(original,GpuFormat.RGBA16_FLOAT,15,1,d->d,d->{throw failure;})));
        alive(original);
    }
}
