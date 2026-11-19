package dev.oculusforge.render.pipeline;

import com.mojang.renderpearl.api.textures.*;
import dev.oculusforge.render.program.ColorBufferFormat;
import dev.oculusforge.render.program.ColorBufferPlan;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Bounded scratch ownership, independently testable without a graphics device. */
public final class DeferredScratchTargets implements AutoCloseable {
    public interface Factory {
        GpuTexture texture(ColorBufferFormat format,int width,int height);
        GpuTextureView view(GpuTexture texture);
    }
    public static final class Accounting {
        private final AtomicInteger live=new AtomicInteger();
        private final AtomicLong bytes=new AtomicLong();
        public int textureCount(){return live.get();}
        public long byteCount(){return bytes.get();}
    }
    private static final Accounting DEFAULT_ACCOUNTING=new Accounting();
    private final Accounting accounting;
    private static final class Entry {
        GpuTexture texture;
        GpuTextureView view;
        long bytes;
        boolean counted;
    }
    private final List<ColorBufferFormat> formats;
    private final Factory factory;
    private final List<Entry> entries;
    private int width,height;
    private boolean complete,closed;

    public DeferredScratchTargets(List<ColorBufferFormat> formats,Factory factory) {
        this(formats,factory,DEFAULT_ACCOUNTING);
    }
    public DeferredScratchTargets(List<ColorBufferFormat> formats,Factory factory,Accounting accounting) {
        this.accounting=Objects.requireNonNull(accounting);
        if(formats.size()>32)throw new IllegalArgumentException("Too many deferred scratch slots");
        this.formats=List.copyOf(formats);this.factory=Objects.requireNonNull(factory);
        entries=new ArrayList<>(formats.size());
    }

    public void ensure(int requestedWidth,int requestedHeight) {
        if(closed)throw new IllegalStateException("Deferred scratch targets are closed");
        if(requestedWidth<=0||requestedHeight<=0)throw new IllegalArgumentException("Invalid deferred viewport");
        long pixels=Math.multiplyExact((long)requestedWidth,requestedHeight);
        long bytes=Math.multiplyExact(pixels,formats.stream().mapToLong(ColorBufferFormat::bytesPerPixel).sum());
        if(bytes>ColorBufferPlan.MAX_SCRATCH_BYTES)throw new IllegalStateException("Deferred scratch budget exceeded");
        if(complete&&width==requestedWidth&&height==requestedHeight)return;
        reset(); // Never retain a second full-sized allocation during resize.
        try {
            for(var format:formats) {
                // Register the entry before creating its resources, including
                // the texture whose view allocation might fail.
                var entry=new Entry();entries.add(entry);entry.bytes=Math.multiplyExact(pixels,format.bytesPerPixel());
                entry.texture=Objects.requireNonNull(factory.texture(format,requestedWidth,requestedHeight),"scratch texture");
                entry.counted=true;accounting.live.incrementAndGet();accounting.bytes.addAndGet(entry.bytes);
                if(entry.texture.isClosed()||entry.texture.getFormat()!=format.gpu()
                    ||entry.texture.getWidth(0)!=requestedWidth||entry.texture.getHeight(0)!=requestedHeight)
                    throw new IllegalStateException("Scratch factory returned a mismatched texture");
                entry.view=Objects.requireNonNull(factory.view(entry.texture),"scratch view");
                if(entry.view.isClosed()||entry.view.texture()!=entry.texture||entry.view.baseMipLevel()!=0
                    ||entry.view.mipLevels()!=1||entry.view.getWidth(0)!=requestedWidth||entry.view.getHeight(0)!=requestedHeight)
                    throw new IllegalStateException("Scratch factory returned a mismatched view");
            }
            width=requestedWidth;height=requestedHeight;complete=true;
        }catch(RuntimeException|Error failure) {
            try{reset();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    public GpuTexture texture(int slot){requireComplete();return entries.get(slot).texture;}
    public GpuTextureView view(int slot){requireComplete();return entries.get(slot).view;}
    private void requireComplete(){if(closed||!complete)throw new IllegalStateException("No complete deferred scratch allocation");}
    public boolean hasOwnedResources(){return !entries.isEmpty();}
    public static int liveCount(){return DEFAULT_ACCOUNTING.textureCount();}
    public static long liveBytes(){return DEFAULT_ACCOUNTING.byteCount();}

    public void reset() {
        complete=false;width=height=0;
        Throwable failure=null;
        for(var entry:entries.reversed())if(entry.view!=null) {
            try{if(!entry.view.isClosed())entry.view.close();}
            catch(RuntimeException|Error error){failure=append(failure,error);}
            if(entry.view.isClosed())entry.view=null;
        }
        for(var entry:entries.reversed())if(entry.texture!=null&&entry.view==null) {
            try{if(!entry.texture.isClosed())entry.texture.close();}
            catch(RuntimeException|Error error){failure=append(failure,error);}
            if(entry.texture.isClosed()) {
                if(entry.counted){entry.counted=false;accounting.live.decrementAndGet();accounting.bytes.addAndGet(-entry.bytes);}
                entry.texture=null;
            }
        }
        entries.removeIf(entry->entry.texture==null&&entry.view==null);
        // Failed handles stay owned and counted; another explicit close/reset
        // can retry them. Do not pretend failed destruction freed GPU storage.
        if(failure==null&&!entries.isEmpty())failure=new IllegalStateException("Deferred resources did not close");
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
    }

    private static Throwable append(Throwable first,Throwable next) {
        if(first==null)return next;if(first!=next)first.addSuppressed(next);return first;
    }
    @Override public void close(){closed=true;reset();}
}
