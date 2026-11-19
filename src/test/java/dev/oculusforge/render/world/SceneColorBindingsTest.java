package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.oculusforge.render.program.ColorBufferFormat;
import org.junit.jupiter.api.Test;

class SceneColorBindingsTest {
    @Test void onlyTheExactMainTargetResolvesToTheFrameColor() {
        var original=new Target(32,16);var foreign=new Target(32,16);
        var color=new Texture(GpuFormat.RGBA16_FLOAT,32,16);var view=new View(color);
        var bindings=new SceneColorBindings(original,color,view,ColorBufferFormat.RGBA16F);
        assertSame(color,bindings.texture(original,original.getColorTexture()));
        assertSame(view,bindings.textureView(original,original.getColorTextureView()));
        assertSame(foreign.getColorTexture(),bindings.texture(foreign,foreign.getColorTexture()));
        assertSame(foreign.getColorTextureView(),bindings.textureView(foreign,foreign.getColorTextureView()));
        assertNull(bindings.texture(null,null));assertFalse(bindings.isMain(foreign));
        assertEquals(GpuFormat.RGBA8_UNORM,original.getColorTexture().getFormat());
    }

    @Test void aVanillaRendererCanCacheTheSameTargetAcrossResizeWithoutCachingClosedSceneStorage() {
        var original=new Target(32,16);RenderTarget cached=original;
        var first=new Texture(GpuFormat.RGBA16_FLOAT,32,16);
        var before=new SceneColorBindings(original,first,new View(first),ColorBufferFormat.RGBA16F);
        assertSame(first,before.texture(cached,cached.getColorTexture()));
        // End frame, resize actual Minecraft storage, and dispose the previous owned scene.
        original.rebind(64,32);first.close();
        var next=new Texture(GpuFormat.RGBA16_FLOAT,64,32);
        var after=new SceneColorBindings(original,next,new View(next),ColorBufferFormat.RGBA16F);
        assertSame(original,cached);assertSame(next,after.texture(cached,cached.getColorTexture()));
        assertFalse(after.texture(cached,null).isClosed());assertEquals(64,after.textureView(cached,null).getWidth(0));
        assertNotSame(first,cached.getColorTexture());assertFalse(cached.getDepthTexture().isClosed());
    }

    @Test void dimensionsFormatViewIdentityAndAliasingRejectBeforeCapture() {
        var original=new Target(32,16);var color=new Texture(GpuFormat.RGBA16_FLOAT,32,16);
        var wrongSize=new Texture(GpuFormat.RGBA16_FLOAT,64,16);
        assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,wrongSize,new View(wrongSize),ColorBufferFormat.RGBA16F));
        assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,color,new View(color),ColorBufferFormat.RGBA8));
        assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,color,new View(wrongSize),ColorBufferFormat.RGBA16F));
        assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,original.getColorTexture(),original.getColorTextureView(),ColorBufferFormat.RGBA8));
    }

    @Test void closedStorageAndMissingBorrowedDepthRejectWithoutClosingTheOriginal() {
        var original=new Target(32,16);var color=new Texture(GpuFormat.RGBA16_FLOAT,32,16);var view=new View(color);
        view.close();assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,color,view,ColorBufferFormat.RGBA16F));
        color.close();assertThrows(IllegalArgumentException.class,()->new SceneColorBindings(original,color,new View(color),ColorBufferFormat.RGBA16F));
        var live=new Texture(GpuFormat.RGBA16_FLOAT,32,16);original.withoutDepth();
        assertThrows(NullPointerException.class,()->new SceneColorBindings(original,live,new View(live),ColorBufferFormat.RGBA16F));
        assertFalse(original.getColorTexture().isClosed());assertFalse(live.isClosed());
    }

    @Test void inactiveScopeReturnsTheActualNewStorageForCachedTargets() {
        var original=new Target(32,16);RenderTarget cached=original;original.rebind(64,32);
        assertSame(cached.getColorTexture(),WorldSceneCapture.colorTexture(cached,cached.getColorTexture()));
        assertSame(cached.getColorTextureView(),WorldSceneCapture.colorView(cached,cached.getColorTextureView()));
        assertFalse(WorldSceneCapture.isMain(cached));
    }

    private static final class Target extends RenderTarget {
        Target(int width,int height) { super("CPU target",true,GpuFormat.RGBA8_UNORM);rebind(width,height); }
        void rebind(int width,int height) {
            if(colorTexture!=null)colorTexture.close();if(depthTexture!=null)depthTexture.close();
            this.width=width;this.height=height;
            colorTexture=new Texture(GpuFormat.RGBA8_UNORM,width,height);colorTextureView=new View(colorTexture);
            depthTexture=new Texture(GpuFormat.D32_FLOAT,width,height);depthTextureView=new View(depthTexture);
        }
        void withoutDepth() { depthTexture=null;depthTextureView=null; }
    }
    private static final class Texture extends GpuTexture {
        private boolean closed;
        Texture(GpuFormat format,int width,int height) { super(15,"CPU color",format,width,height,1,1); }
        @Override public void close() { closed=true; }
        @Override public boolean isClosed() { return closed; }
    }
    private static final class View extends GpuTextureView {
        private boolean closed;
        View(GpuTexture texture) { super(texture,0,1); }
        @Override public void close() { closed=true; }
        @Override public boolean isClosed() { return closed; }
    }
}
