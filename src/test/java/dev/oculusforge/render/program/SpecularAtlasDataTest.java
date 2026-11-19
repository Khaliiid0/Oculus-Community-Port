package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SpecularAtlasDataTest {
    private static final String NAME="minecraft:textures/block/stone_s.png";
    private static final SpecularAtlasData.Sprite SPRITE=new SpecularAtlasData.Sprite(NAME,0,0,2,2,0);
    private static byte[] png(int width,int height,int... argb) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<width*height;i++)image.setRGB(i%width,i/width,argb[i%argb.length]);
        var output=new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image,"png",output));image.flush();return output.toByteArray();
    }
    private static SpecularAtlasData prepare(int width,int height,int mips,List<SpecularAtlasData.Sprite> sprites,
        byte[] png,String metadata,SpecularAtlasData.Format format) throws Exception {
        return SpecularAtlasData.prepare(width,height,mips,sprites,format,
            name->Optional.of(new SpecularAtlasData.Image(png,metadata)));
    }
    @Test void absentFallbackRequiresCheckingEveryLiveSpriteAndCostsFourBytes() throws Exception {
        var visited=new AtomicInteger();
        var result=SpecularAtlasData.prepare(8192,8192,5,List.of(SPRITE,
            new SpecularAtlasData.Sprite("mod:textures/block/b_s.png",2,0,2,2,0)),SpecularAtlasData.Format.LINEAR,
            name->{visited.incrementAndGet();return Optional.empty();});
        assertEquals(2,visited.get());assertEquals(0,result.mapCount());assertEquals(1,result.width(0));
        assertEquals(1,result.height(0));assertEquals(1,result.mipCount());assertEquals(4,result.byteSize());
        assertEquals(0,result.pixelAbgr(0,0));
    }
    @Test void staticMapsPreserveRgbaIncludingAlphaAndTransparentRgb() throws Exception {
        var result=prepare(2,2,1,List.of(SPRITE),png(2,2,0x12345678,0x00abcdef),null,SpecularAtlasData.Format.LINEAR);
        assertEquals(1,result.mapCount());assertEquals(0x12785634,result.pixelAbgr(0,0));
        assertEquals(0x00efcdab,result.pixelAbgr(0,1));assertEquals(16,result.byteSize());
    }
    @Test void paddingAndMipmapsStayAtTheAlbedoCoordinatesWithoutCrossSpriteBleed() throws Exception {
        var a=new SpecularAtlasData.Sprite(NAME,0,0,4,4,2);
        var b=new SpecularAtlasData.Sprite("mod:textures/block/b_s.png",8,0,4,4,2);
        byte[] red=png(4,4,0xff330000),blue=png(4,4,0xff000077);
        var result=SpecularAtlasData.prepare(16,8,2,List.of(a,b),SpecularAtlasData.Format.LINEAR,
            name->Optional.of(new SpecularAtlasData.Image(name.equals(NAME)?red:blue,null)));
        assertEquals(0xff000033,result.pixelAbgr(0,0));assertEquals(0xff000033,result.pixelAbgr(0,7));
        assertEquals(0xff770000,result.pixelAbgr(0,8));assertEquals(0xff770000,result.pixelAbgr(0,127));
        assertEquals(0xff000033,result.pixelAbgr(1,3));assertEquals(0xff770000,result.pixelAbgr(1,4));
        assertEquals(640,result.byteSize());
    }
    @Test void missingSpriteRegionsRemainTheDefinedZeroValue() throws Exception {
        byte[] image=png(2,2,0x80604020);
        var result=SpecularAtlasData.prepare(4,2,1,List.of(SPRITE,
            new SpecularAtlasData.Sprite("mod:textures/block/absent_s.png",2,0,2,2,0)),SpecularAtlasData.Format.LINEAR,
            name->name.equals(NAME)?Optional.of(new SpecularAtlasData.Image(image,null)):Optional.empty());
        assertEquals(1,result.mapCount());assertEquals(0,result.pixelAbgr(0,2));assertEquals(0,result.pixelAbgr(0,7));
    }
    @Test void linearMipmapsAverageDataChannelsWithoutGammaOrAlphaPremultiplication() throws Exception {
        var result=prepare(2,2,2,List.of(SPRITE),png(2,2,0xff0ae600,0x0a1ee640,0x1432e741,0xff4600c8),null,SpecularAtlasData.Format.LINEAR);
        assertEquals(0x8752ac28,result.pixelAbgr(1,0));
    }
    @Test void labPbrMipmapsPreserveCategoricalMetalPorosityAndEmissionChannels() throws Exception {
        var result=prepare(2,2,2,List.of(SPRITE),png(2,2,0xff0ae600,0x0a1ee640,0x1432e741,0xff4600c8),null,SpecularAtlasData.Format.LAB_PBR);
        assertEquals(0xff20e628,result.pixelAbgr(1,0));
    }
    @Test void animatedOrUnknownMetadataNeverSilentlyBecomesAStaticMap() throws Exception {
        byte[] image=png(2,2,0xffffffff);
        for(String metadata:List.of("{\"animation\":{}}","{\"texture\":{\"blur\":true}}","[]","{"," ".repeat(4097)))
            assertThrows(ShaderCompatibilityException.class,()->prepare(2,2,1,List.of(SPRITE),image,metadata,SpecularAtlasData.Format.LINEAR));
        assertEquals(1,prepare(2,2,1,List.of(SPRITE),image,"{  }",SpecularAtlasData.Format.LINEAR).mapCount());
    }
    @Test void resizingAndMisalignedOrOutOfBoundsPlacementReject() throws Exception {
        byte[] image=png(2,2,0xff010203);
        for(var sprite:List.of(new SpecularAtlasData.Sprite(NAME,0,0,1,2,0),new SpecularAtlasData.Sprite(NAME,1,0,2,2,0),
            new SpecularAtlasData.Sprite(NAME,-1,0,2,2,0),new SpecularAtlasData.Sprite(NAME,0,0,2,2,1),
            new SpecularAtlasData.Sprite(NAME,Integer.MAX_VALUE,0,2,2,Integer.MAX_VALUE)))
            assertThrows(ShaderCompatibilityException.class,()->prepare(2,2,2,List.of(sprite),image,null,SpecularAtlasData.Format.LINEAR));
    }
    @Test void badResourceLocationsDuplicatesAndSpriteCountsRejectBeforeFallback() {
        for(String resource:List.of("../outside.png","minecraft:../outside_s.png","minecraft:C:\\x.png"))
            assertThrows(ShaderCompatibilityException.class,()->SpecularAtlasData.prepare(2,2,1,
                List.of(new SpecularAtlasData.Sprite(resource,0,0,2,2,0)),SpecularAtlasData.Format.LINEAR,name->Optional.empty()));
        assertThrows(ShaderCompatibilityException.class,()->SpecularAtlasData.prepare(2,2,1,List.of(SPRITE,SPRITE),
            SpecularAtlasData.Format.LINEAR,name->Optional.empty()));
        assertThrows(ShaderCompatibilityException.class,()->SpecularAtlasData.prepare(2,2,1,
            java.util.Collections.nCopies(65_537,SPRITE),SpecularAtlasData.Format.LINEAR,name->Optional.empty()));
    }
    @Test void oversizedAtlasAndEncodedPayloadRejectBeforeLargeAtlasAllocation() throws Exception {
        byte[] image=png(2,2,0xffffffff);
        assertThrows(ShaderCompatibilityException.class,()->prepare(8192,8192,1,List.of(SPRITE),image,null,SpecularAtlasData.Format.LINEAR));
        assertThrows(ShaderCompatibilityException.class,()->prepare(2,2,1,List.of(SPRITE),new byte[33*1024*1024],null,SpecularAtlasData.Format.LINEAR));
        for(int mips:List.of(0,3,16))assertThrows(ShaderCompatibilityException.class,
            ()->prepare(2,2,mips,List.of(SPRITE),image,null,SpecularAtlasData.Format.LINEAR));
    }
    @Test void pngCorruptionOrResourceFailureIsNotTreatedAsMissingData() throws Exception {
        byte[] image=png(2,2,0xffffffff);image[image.length-1]^=1;
        assertThrows(ShaderCompatibilityException.class,()->prepare(2,2,1,List.of(SPRITE),image,null,SpecularAtlasData.Format.LINEAR));
        assertThrows(java.io.IOException.class,()->SpecularAtlasData.prepare(2,2,1,List.of(SPRITE),SpecularAtlasData.Format.LINEAR,
            name->{throw new java.io.IOException("unreadable resource");}));
    }
    @Test void textureFormatSelectionIsBoundedAndExplicit() throws Exception {
        assertEquals(SpecularAtlasData.Format.LINEAR,SpecularAtlasData.parseFormat(new byte[0]));
        for(String format:List.of("lab-pbr","lab-pbr/1.3"))assertEquals(SpecularAtlasData.Format.LAB_PBR,
            SpecularAtlasData.parseFormat(("format="+format).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(String properties:List.of("format=unknown","format=lab-pbr/99","format=\\uGGGG"," ".repeat(4097)))
            assertThrows(ShaderCompatibilityException.class,()->SpecularAtlasData.parseFormat(properties.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
