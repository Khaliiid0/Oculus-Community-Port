package dev.oculusforge.render.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.render.program.ColorBufferFormat;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;

class DeferredResourcesTest {
    static class Texture implements GpuTexture {
        int width=16,height=8,layers=1,mips=1,usage=15,failCloses,closes;
        GpuFormat format=GpuFormat.RGBA8_UNORM;
        boolean closed;
        Runnable onClose=()->{};
        public int getWidth(int mip){return width;} public int getHeight(int mip){return height;}
        public int getDepthOrLayers(){return layers;} public int getMipLevels(){return mips;}
        public GpuFormat getFormat(){return format;} public int usage(){return usage;}
        public String getLabel(){return "fake";} public boolean isClosed(){return closed;}
        public void close(){closes++;if(failCloses-->0)throw new IllegalStateException("texture close");onClose.run();closed=true;}
    }
    static class View implements GpuTextureView {
        final Texture texture;boolean closed;int failCloses,closes;Runnable onClose=()->{};
        View(Texture texture){this.texture=texture;}
        public boolean isClosed(){return closed;} public GpuTexture texture(){return texture;}
        public int baseMipLevel(){return 0;} public int mipLevels(){return 1;}
        public int getWidth(int mip){return texture.width;} public int getHeight(int mip){return texture.height;}
        public void close(){closes++;if(failCloses-->0)throw new IllegalStateException("view close");onClose.run();closed=true;}
    }
    static class Factory implements DeferredScratchTargets.Factory {
        final List<Texture> textures=new ArrayList<>();final List<View> views=new ArrayList<>();
        final List<String> disposal=new ArrayList<>();int viewCalls,textureCalls,failView=-1,failTexture=-1;
        Throwable allocationFailure=new IllegalStateException("allocation");
        public GpuTexture texture(ColorBufferFormat format,int width,int height) {
            if(textureCalls++==failTexture)rethrow(allocationFailure);
            var texture=new Texture();texture.width=width;texture.height=height;texture.format=format.gpu();
            int id=textures.size();texture.onClose=()->{
                assertTrue(views.stream().noneMatch(view->view.texture==texture&&!view.closed));disposal.add("t"+id);
            };
            textures.add(texture);return texture;
        }
        public GpuTextureView view(GpuTexture input) {
            if(viewCalls++==failView)rethrow(allocationFailure);
            var view=new View((Texture)input);int id=views.size();view.onClose=()->disposal.add("v"+id);
            views.add(view);return view;
        }
    }
    static void rethrow(Throwable failure){if(failure instanceof Error error)throw error;throw (RuntimeException)failure;}
    DeferredScratchTargets targets(Factory factory){return new DeferredScratchTargets(List.of(ColorBufferFormat.RGBA8,ColorBufferFormat.RGBA16F),factory);}
    @AfterEach void noTrackedResourcesRemain(){assertEquals(0,DeferredScratchTargets.liveCount());assertEquals(0,DeferredScratchTargets.liveBytes());}

    @Test void centerHistoryAccountingIsIsolatedFromDeferredScratchAndRollsBackOnFailure() {
        var counter=new DeferredScratchTargets.Accounting();var factory=new Factory();factory.failView=1;
        try(var targets=new DeferredScratchTargets(List.of(ColorBufferFormat.R32F,ColorBufferFormat.R32F),factory,counter)) {
            assertThrows(IllegalStateException.class,()->targets.ensure(1,1));
            assertEquals(0,counter.textureCount());assertEquals(0,counter.byteCount());
            factory.failView=-1;targets.ensure(1,1);assertEquals(2,counter.textureCount());assertEquals(8,counter.byteCount());
            assertEquals(0,DeferredScratchTargets.liveCount());
        }
        assertEquals(0,counter.textureCount());assertEquals(0,counter.byteCount());
    }

    @Test void secondViewFailureRollsBackBothTexturesAndTheFirstViewThenCanRetry() {
        var factory=new Factory();factory.failView=1;
        try(var targets=targets(factory)) {
            assertSame(factory.allocationFailure,assertThrows(IllegalStateException.class,()->targets.ensure(16,8)));
            assertEquals(2,factory.textures.size());assertEquals(1,factory.views.size());
            assertTrue(factory.textures.stream().allMatch(texture->texture.closed));assertTrue(factory.views.getFirst().closed);
            assertFalse(targets.hasOwnedResources());assertThrows(IllegalStateException.class,()->targets.view(0));
            factory.failView=-1;targets.ensure(16,8);assertNotNull(targets.view(1));
        }
    }
    @Test void textureAllocationFailureAlsoDisposesEarlierCompleteSlots() {
        var factory=new Factory();factory.failTexture=1;
        try(var targets=targets(factory)) {
            assertSame(factory.allocationFailure,assertThrows(IllegalStateException.class,()->targets.ensure(16,8)));
            assertEquals(List.of("v0","t0"),factory.disposal);assertFalse(targets.hasOwnedResources());
        }
    }
    @Test void fatalAllocationErrorIsNotSwallowedAndStillRollsBack() {
        var factory=new Factory();factory.failView=1;factory.allocationFailure=new AssertionError("fatal allocation");
        try(var targets=targets(factory)) {
            assertSame(factory.allocationFailure,assertThrows(AssertionError.class,()->targets.ensure(16,8)));
            assertTrue(factory.textures.stream().allMatch(texture->texture.closed));
        }
    }
    @Test void allViewsCloseBeforeAnyParentTextureAndRepeatedCloseDoesNothing() {
        var factory=new Factory();var targets=targets(factory);targets.ensure(16,8);
        assertEquals(2,DeferredScratchTargets.liveCount());assertEquals(16L*8*12,DeferredScratchTargets.liveBytes());
        targets.close();targets.close();assertEquals(List.of("v1","v0","t1","t0"),factory.disposal);
        assertTrue(factory.textures.stream().allMatch(texture->texture.closes==1));
        assertThrows(IllegalStateException.class,()->targets.ensure(16,8));
    }
    @Test void failedViewDestructionKeepsItsParentOwnedAndCountedUntilRetry() {
        var factory=new Factory();var targets=targets(factory);targets.ensure(16,8);factory.views.getLast().failCloses=1;
        assertThrows(IllegalStateException.class,targets::close);
        assertEquals(1,DeferredScratchTargets.liveCount());assertTrue(targets.hasOwnedResources());
        assertEquals(0,factory.textures.getLast().closes,"unclosed child prevents parent destruction");
        targets.close();assertFalse(targets.hasOwnedResources());assertEquals(1,factory.textures.getLast().closes);
    }
    @Test void failedTextureDestructionIsNotForgottenOrFalselyCountedAsFreed() {
        var factory=new Factory();var targets=targets(factory);targets.ensure(16,8);factory.textures.getLast().failCloses=1;
        assertThrows(IllegalStateException.class,targets::close);assertEquals(1,DeferredScratchTargets.liveCount());
        targets.close();assertFalse(targets.hasOwnedResources());assertEquals(2,factory.textures.getLast().closes);
    }
    @Test void cleanupFailureIsSuppressedWithoutMaskingTheOriginalAllocationFailure() {
        var factory=new Factory(){@Override public GpuTextureView view(GpuTexture texture){
            if(viewCalls==1)textures.getFirst().failCloses=1;return super.view(texture);
        }};factory.failView=1;
        try(var targets=targets(factory)) {
            var error=assertThrows(IllegalStateException.class,()->targets.ensure(16,8));
            assertSame(factory.allocationFailure,error);assertEquals("texture close",error.getSuppressed()[0].getMessage());
            assertEquals(1,DeferredScratchTargets.liveCount());targets.reset();assertFalse(targets.hasOwnedResources());
        }
    }
    @Test void healthySameSizeReusesStorageAndResizeDisposesBeforeAllocatingNewStorage() {
        var factory=new Factory();try(var targets=targets(factory)) {
            targets.ensure(16,8);var old=targets.texture(0);targets.ensure(16,8);assertSame(old,targets.texture(0));
            assertEquals(2,factory.textureCalls);targets.ensure(32,16);assertTrue(old.isClosed());
            assertEquals(4,factory.textureCalls);assertEquals(2,DeferredScratchTargets.liveCount());
            assertEquals(32L*16*12,DeferredScratchTargets.liveBytes());
        }
    }
    @Test void invalidOrOverBudgetResizeDoesNotDestroyTheHealthyAllocation() {
        var factory=new Factory();try(var targets=targets(factory)) {
            targets.ensure(16,8);var old=targets.texture(0);
            assertThrows(IllegalArgumentException.class,()->targets.ensure(0,8));
            assertThrows(IllegalStateException.class,()->targets.ensure(16384,16384));
            assertSame(old,targets.texture(0));assertFalse(old.isClosed());assertEquals(2,factory.textureCalls);
        }
    }

    record Transfer(Texture source,Texture target){}
    static class Encoder {
        final List<Transfer> copies=new ArrayList<>();int failAt=-1;
        final RuntimeException failure=new IllegalStateException("copy backend");
        CommandEncoder proxy(){return (CommandEncoder)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CommandEncoder.class},(object,method,args)->{
            if(!method.getName().equals("copyTextureToTexture"))throw new AssertionError(method.getName());
            if(copies.size()==failAt)throw failure;
            copies.add(new Transfer((Texture)args[0],(Texture)args[1]));return null;
        });}
    }
    DeferredTextureCopies.Copy copy(Texture source,Texture target){return new DeferredTextureCopies.Copy(source,target);}

    @Test void laterBadTargetIsRejectedBeforeTheFirstGpuWrite() {
        var encoder=new Encoder();var bad=new Texture();bad.format=GpuFormat.RGBA16_FLOAT;
        var batch=new DeferredTextureCopies(List.of(copy(new Texture(),new Texture()),copy(new Texture(),bad)),16,8);
        assertThrows(IllegalStateException.class,()->batch.commit(encoder.proxy()));assertTrue(encoder.copies.isEmpty());
    }
    @Test void dimensionUsageClosedLayerAndMipErrorsAllRejectBeforeAnyWrite() {
        for(int test=0;test<6;test++) {
            var encoder=new Encoder();var bad=new Texture();
            switch(test){case 0->bad.width=17;case 1->bad.height=9;case 2->bad.usage=GpuTexture.USAGE_COPY_SRC;
                case 3->bad.closed=true;case 4->bad.layers=2;case 5->bad.mips=0;}
            var batch=new DeferredTextureCopies(List.of(copy(new Texture(),new Texture()),copy(new Texture(),bad)),16,8);
            assertThrows(IllegalStateException.class,()->batch.commit(encoder.proxy()));assertTrue(encoder.copies.isEmpty());
        }
    }
    @Test void badSourceDimensionsAndCopyUsageAlsoRejectBeforeAnyWrite() {
        var encoder=new Encoder();var source=new Texture();source.width=1;
        assertThrows(IllegalStateException.class,()->new DeferredTextureCopies(List.of(copy(source,new Texture())),16,8).commit(encoder.proxy()));
        source.width=16;source.usage=GpuTexture.USAGE_COPY_DST;
        assertThrows(IllegalStateException.class,()->new DeferredTextureCopies(List.of(copy(source,new Texture())),16,8).commit(encoder.proxy()));
        assertTrue(encoder.copies.isEmpty());
    }
    @Test void selfAndCrossCopyAliasingAndDuplicateDestinationsAreRejected() {
        var encoder=new Encoder();var shared=new Texture();
        for(var copies:List.of(List.of(copy(shared,shared)),List.of(copy(new Texture(),shared),copy(shared,new Texture())),
            List.of(copy(new Texture(),shared),copy(new Texture(),shared))))
            assertThrows(IllegalStateException.class,()->new DeferredTextureCopies(copies,16,8).commit(encoder.proxy()));
        assertTrue(encoder.copies.isEmpty());
    }
    @Test void validatedBatchPreservesCommandOrderAndCanReadOneSourceTwice() {
        var encoder=new Encoder();var source=new Texture();var a=new Texture();var b=new Texture();
        new DeferredTextureCopies(List.of(copy(source,a),copy(source,b)),16,8).commit(encoder.proxy());
        assertEquals(List.of(new Transfer(source,a),new Transfer(source,b)),encoder.copies);
    }
    @Test void backendFailureStopsFurtherWritesAndPreservesTheOriginalError() {
        var encoder=new Encoder();encoder.failAt=1;
        var batch=new DeferredTextureCopies(List.of(copy(new Texture(),new Texture()),copy(new Texture(),new Texture()),
            copy(new Texture(),new Texture())),16,8);
        assertSame(encoder.failure,assertThrows(IllegalStateException.class,()->batch.commit(encoder.proxy())));
        assertEquals(1,encoder.copies.size(),"earlier issued copies are not claimed to roll back atomically");
    }
    @Test void emptyBatchIsSafeAndInvalidBatchDimensionsOrSizeRejectEarly() {
        var encoder=new Encoder();new DeferredTextureCopies(List.of(),16,8).commit(encoder.proxy());assertTrue(encoder.copies.isEmpty());
        assertThrows(IllegalArgumentException.class,()->new DeferredTextureCopies(List.of(),0,8));
        assertThrows(IllegalArgumentException.class,()->new DeferredTextureCopies(Collections.nCopies(17,copy(new Texture(),new Texture())),16,8));
    }
}
