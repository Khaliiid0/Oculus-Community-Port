package dev.oculusforge.render.program;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Compatibility facade; both material atlases share one bounded decoder/stitcher. */
public final class SpecularAtlasData {
    public static final int MAX_SPRITES=StaticMaterialAtlasData.MAX_SPRITES;
    public static final long MAX_ATLAS_BYTES=StaticMaterialAtlasData.MAX_ATLAS_BYTES;
    public static final long MAX_ENCODED_BYTES=StaticMaterialAtlasData.MAX_ENCODED_BYTES;
    public enum Format { LINEAR, LAB_PBR }
    public record Sprite(String resource,int x,int y,int width,int height,int padding) {}
    public record Image(byte[] png,String metadata) {}
    @FunctionalInterface public interface Resources {
        Optional<Image> read(String resource) throws IOException,ShaderCompatibilityException;
    }
    private final StaticMaterialAtlasData atlas;
    private SpecularAtlasData(StaticMaterialAtlasData atlas){this.atlas=atlas;}
    public static SpecularAtlasData prepare(int width,int height,int mipCount,List<Sprite> sprites,Format format,Resources resources)
        throws IOException,ShaderCompatibilityException {
        java.util.Objects.requireNonNull(format);java.util.Objects.requireNonNull(resources);
        if(sprites.size()>MAX_SPRITES)throw new ShaderCompatibilityException("Too many material atlas sprites");
        return new SpecularAtlasData(StaticMaterialAtlasData.prepare(StaticMaterialAtlasData.Kind.SPECULAR,width,height,mipCount,
            sprites.stream().map(s->new StaticMaterialAtlasData.Sprite(s.resource(),s.x(),s.y(),s.width(),s.height(),s.padding())).toList(),
            StaticMaterialAtlasData.Format.valueOf(format.name()),name->resources.read(name).map(i->new StaticMaterialAtlasData.Image(i.png(),i.metadata()))));
    }
    public static Format parseFormat(byte[] encoded) throws ShaderCompatibilityException {
        return Format.valueOf(StaticMaterialAtlasData.parseFormat(encoded).name());
    }
    public StaticMaterialAtlasData atlas(){return atlas;}
    public int width(int level){return atlas.width(level);}
    public int height(int level){return atlas.height(level);}
    public int mipCount(){return atlas.mipCount();}
    public int mapCount(){return atlas.mapCount();}
    public int pixelAbgr(int level,int index){return atlas.pixelAbgr(level,index);}
    public long byteSize(){return atlas.byteSize();}
}
