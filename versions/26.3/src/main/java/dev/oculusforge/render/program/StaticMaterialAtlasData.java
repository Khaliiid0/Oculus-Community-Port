package dev.oculusforge.render.program;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Static material data at exact albedo sprite coordinates; no color-space conversion. */
public final class StaticMaterialAtlasData {
    public enum Kind {
        SPECULAR("specular", "_s", "OculusSpecular", 0),
        NORMAL("normal", "_n", "OculusNormals", 0xffff7f7f);
        private final String label,suffix,uniform;
        private final int defaultAbgr;
        Kind(String label,String suffix,String uniform,int defaultAbgr){this.label=label;this.suffix=suffix;this.uniform=uniform;this.defaultAbgr=defaultAbgr;}
        public String label(){return label;}
        public String suffix(){return suffix;}
        public String uniform(){return uniform;}
        public int defaultAbgr(){return defaultAbgr;}
    }
    public static final int MAX_SPRITES = 65_536;
    public static final long MAX_ATLAS_BYTES = 48L * 1024 * 1024;
    public static final long MAX_ENCODED_BYTES = 32L * 1024 * 1024;
    public enum Format { LINEAR, LAB_PBR }
    public record Sprite(String resource, int x, int y, int width, int height, int padding) { }
    public record Image(byte[] png, String metadata) { }
    @FunctionalInterface public interface Resources {
        Optional<Image> read(String resource) throws IOException, ShaderCompatibilityException;
    }
    private final int width, height, maps;
    private final Kind kind;
    private final int[][] levels;
    private StaticMaterialAtlasData(Kind kind,int width, int height, int maps, int[][] levels) {
        this.kind=kind;this.width=width;this.height=height;this.maps=maps;this.levels=levels;
    }

    public static StaticMaterialAtlasData prepare(Kind kind,int width, int height, int mipCount,
        List<Sprite> sprites, Format format, Resources resources) throws IOException, ShaderCompatibilityException {
        if(width<1||height<1||width>16384||height>16384||mipCount<1||mipCount>15
            ||(width>>(mipCount-1))<1||(height>>(mipCount-1))<1||sprites.size()>MAX_SPRITES)
            throw new ShaderCompatibilityException("Invalid or oversized material atlas layout");
        java.util.Objects.requireNonNull(kind);java.util.Objects.requireNonNull(format);java.util.Objects.requireNonNull(resources);
        int[][] atlas=null;int maps=0;long encodedBytes=0,decodedBytes=0;
        var seen=new HashSet<String>();
        for(Sprite sprite:sprites) {
            String name=sprite.resource();
            if(name==null||name.length()>512||!name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                ||name.contains("..")||!seen.add(name))
                throw new ShaderCompatibilityException("Invalid or duplicate material resource location");
            var resource=resources.read(name);
            if(resource.isEmpty())continue;
            Image input=resource.get();
            validateMetadata(input.metadata());
            encodedBytes+=input.png().length;
            if(encodedBytes>MAX_ENCODED_BYTES)throw new ShaderCompatibilityException("Material PNGs exceed 32 MiB encoded budget");
            var image=PackImageData.decode(input.png(),PackImageData.MAX_TOTAL_BYTES-decodedBytes,false,true);
            decodedBytes+=image.byteSize();
            if(image.width()!=sprite.width()||image.height()!=sprite.height())
                throw new ShaderCompatibilityException("Material map resizing/animation is not supported: "+name);
            int alignment=1<<(mipCount-1);
            long paddedWidth=(long)sprite.width()+2L*sprite.padding(),paddedHeight=(long)sprite.height()+2L*sprite.padding();
            if(sprite.x()<0||sprite.y()<0||sprite.width()<1||sprite.height()<1||sprite.padding()<0
                ||sprite.x()+paddedWidth>width||sprite.y()+paddedHeight>height
                ||(sprite.x()|sprite.y()|sprite.width()|sprite.height()|sprite.padding())%alignment!=0)
                throw new ShaderCompatibilityException("Unsupported material sprite placement: "+name);
            if(atlas==null) {
                long bytes=0;
                for(int level=0;level<mipCount;level++)bytes+=(long)(width>>level)*(height>>level)*4;
                if(bytes>MAX_ATLAS_BYTES)throw new ShaderCompatibilityException("Material atlas exceeds 48 MiB mip budget");
                atlas=new int[mipCount][];
                for(int level=0;level<mipCount;level++) {
                    atlas[level]=new int[(width>>level)*(height>>level)];
                    if(kind.defaultAbgr()!=0)java.util.Arrays.fill(atlas[level],kind.defaultAbgr());
                }
            }
            int[] pixels=new int[image.width()*image.height()];
            for(int i=0;i<pixels.length;i++)pixels[i]=image.pixelAbgr(i);
            for(int level=0;level<mipCount;level++) {
                int w=sprite.width()>>level,h=sprite.height()>>level,pad=sprite.padding()>>level;
                int x0=sprite.x()>>level,y0=sprite.y()>>level,stride=width>>level;
                for(int y=-pad;y<h+pad;y++)for(int x=-pad;x<w+pad;x++)
                    atlas[level][(y0+y+pad)*stride+x0+x+pad]=pixels[Math.clamp(y,0,h-1)*w+Math.clamp(x,0,w-1)];
                // Normal data (including LabPBR AO/height) uses independent linear
                // channel averages, matching Iris; no gamma or RGB renormalization.
                if(level+1<mipCount)pixels=downsample(pixels,w,h,kind==Kind.NORMAL?Format.LINEAR:format);
            }
            maps++;
        }
        // Defined defaults, used ONLY after checking every live sprite.
        return atlas==null?new StaticMaterialAtlasData(kind,1,1,0,new int[][]{{kind.defaultAbgr()}})
            :new StaticMaterialAtlasData(kind,width,height,maps,atlas);
    }

    private static void validateMetadata(String metadata) throws ShaderCompatibilityException {
        if(metadata==null)return;
        if(metadata.length()>4096)throw new ShaderCompatibilityException("Material metadata exceeds 4 KiB");
        if(!metadata.trim().matches("\\{\\s*}"))
            throw new ShaderCompatibilityException("Material animation/texture metadata is not implemented");
    }

    public static Format parseFormat(byte[] encoded) throws ShaderCompatibilityException {
        return MaterialFormat.parse(encoded).atlasFormat();
    }

    private static int[] downsample(int[] pixels,int width,int height,Format format) {
        int[] result=new int[(width/2)*(height/2)];
        for(int y=0;y<height/2;y++)for(int x=0;x<width/2;x++) {
            int i=y*2*width+x*2;
            int a=pixels[i],b=pixels[i+1],c=pixels[i+width],d=pixels[i+width+1],pixel=0;
            for(int shift=0;shift<32;shift+=8)pixel|=blend(a>>>shift&255,b>>>shift&255,c>>>shift&255,d>>>shift&255,
                format==Format.LAB_PBR?shift:0)<<shift;
            result[y*(width/2)+x]=pixel;
        }
        return result;
    }
    private static int group(int value,int channel) {
        return switch(channel) {case 8->value<230?0:value-229;case 16->value<65?0:1;case 24->value<255?0:1;default->0;};
    }
    /** LabPBR categorical channels use majority type, then first-sample tie breaking. */
    private static int blend(int a,int b,int c,int d,int channel) {
        int ga=group(a,channel),gb=group(b,channel),gc=group(c,channel),gd=group(d,channel);
        int winner=ga,count=0;
        for(int i=0;i<4;i++) {
            int candidate=switch(i){case 0->ga;case 1->gb;case 2->gc;default->gd;};
            int matches=(ga==candidate?1:0)+(gb==candidate?1:0)+(gc==candidate?1:0)+(gd==candidate?1:0);
            if(matches>count){winner=candidate;count=matches;}
        }
        return ((ga==winner?a:0)+(gb==winner?b:0)+(gc==winner?c:0)+(gd==winner?d:0))/count;
    }
    public int width(int level){return width>>level;}
    public Kind kind(){return kind;}
    public int height(int level){return height>>level;}
    public int mipCount(){return levels.length;}
    public int mapCount(){return maps;}
    public int pixelAbgr(int level,int index){return levels[level][index];}
    public long byteSize(){long bytes=0;for(int[] level:levels)bytes+=(long)level.length*4;return bytes;}
}
