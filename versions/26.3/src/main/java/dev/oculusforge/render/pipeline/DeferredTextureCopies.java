package dev.oculusforge.render.pipeline;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.util.*;

/** Render-thread handoff: validate the ENTIRE batch before issuing any GPU write. */
public final class DeferredTextureCopies {
    public record Copy(GpuTexture source, GpuTexture target) {
        public Copy { Objects.requireNonNull(source); Objects.requireNonNull(target); }
    }
    private final List<Copy> copies;
    private final int width, height;

    public DeferredTextureCopies(List<Copy> copies, int width, int height) {
        if(width<=0||height<=0||copies.size()>16)throw new IllegalArgumentException("Invalid deferred copy batch");
        this.copies=List.copyOf(copies);this.width=width;this.height=height;
    }

    void validate() {
        var sources=Collections.newSetFromMap(new IdentityHashMap<GpuTexture,Boolean>());
        var targets=Collections.newSetFromMap(new IdentityHashMap<GpuTexture,Boolean>());
        for(var copy:copies) {
            var source=copy.source();var target=copy.target();
            if(source.isClosed()||target.isClosed()||source.getFormat()!=target.getFormat()
                ||source.getWidth(0)!=width||source.getHeight(0)!=height
                ||target.getWidth(0)!=width||target.getHeight(0)!=height
                ||source.getDepthOrLayers()!=1||target.getDepthOrLayers()!=1
                ||source.getMipLevels()<1||target.getMipLevels()<1
                ||(source.usage()&GpuTexture.USAGE_COPY_SRC)==0||(target.usage()&GpuTexture.USAGE_COPY_DST)==0)
                throw new IllegalStateException("Invalid deferred handoff copy");
            sources.add(source);
            if(!targets.add(target))throw new IllegalStateException("Duplicate deferred handoff destination");
        }
        for(var source:sources)if(targets.contains(source))
            throw new IllegalStateException("Deferred handoff source aliases a destination");
    }

    public void commit(CommandEncoder encoder) {
        Objects.requireNonNull(encoder);
        validate();
        // A backend failure after a command is issued is NOT an atomic pixel
        // rollback. Propagate it; the caller retires this failed generation.
        for(var copy:copies)
            encoder.copyTextureToTexture(copy.source(),copy.target(),0,0,0,0,0,width,height);
    }
}
