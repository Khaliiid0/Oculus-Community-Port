package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NormalAtlasTest {
    private static final String NAME="minecraft:textures/block/stone_n.png";
    private static final StaticMaterialAtlasData.Sprite SPRITE=new StaticMaterialAtlasData.Sprite(NAME,0,0,2,2,0);
    private static final String V="void main(){gl_Position=ftransform();}";
    private static byte[] png(int width,int height,int... argb) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<width*height;i++)image.setRGB(i%width,i/width,argb[i%argb.length]);
        var out=new ByteArrayOutputStream();assertTrue(javax.imageio.ImageIO.write(image,"png",out));image.flush();return out.toByteArray();
    }
    private static StaticMaterialAtlasData normal(int width,int height,int mips,List<StaticMaterialAtlasData.Sprite> sprites,
        byte[] image,String metadata,StaticMaterialAtlasData.Format format) throws Exception {
        return StaticMaterialAtlasData.prepare(StaticMaterialAtlasData.Kind.NORMAL,width,height,mips,sprites,format,
            name->Optional.of(new StaticMaterialAtlasData.Image(image,metadata)));
    }
    @Test void absentNormalMapsCheckEverySpriteBeforeUsingTheFourByteFlatDefault() throws Exception {
        var visited=new AtomicInteger();
        var data=StaticMaterialAtlasData.prepare(StaticMaterialAtlasData.Kind.NORMAL,8192,8192,5,
            List.of(SPRITE,new StaticMaterialAtlasData.Sprite("mod:textures/block/absent_n.png",2,0,2,2,0)),
            StaticMaterialAtlasData.Format.LINEAR,name->{visited.incrementAndGet();return Optional.empty();});
        assertEquals(2,visited.get());assertEquals(0,data.mapCount());assertEquals(1,data.width(0));
        assertEquals(1,data.height(0));assertEquals(1,data.mipCount());assertEquals(4,data.byteSize());
        assertEquals(0xffff7f7f,data.pixelAbgr(0,0));
    }
    @Test void rawNormalRgbaRetainsAoHeightAndTransparentRgb() throws Exception {
        var data=normal(2,2,1,List.of(SPRITE),png(2,2,0x12345678,0x00abcdef),null,StaticMaterialAtlasData.Format.LAB_PBR);
        assertEquals(0x12785634,data.pixelAbgr(0,0));assertEquals(0x00efcdab,data.pixelAbgr(0,1));
    }
    @Test void labNormalMipsAreLinearDataNotCategoricalGammaOrPremultiplied() throws Exception {
        var data=normal(2,2,2,List.of(SPRITE),png(2,2,0xff0ae600,0x0a1ee640,0x1432e741,0xff4600c8),null,StaticMaterialAtlasData.Format.LAB_PBR);
        assertEquals(0x8752ac28,data.pixelAbgr(1,0));
    }
    @Test void paddingMipPlacementAndMissingRegionsPreserveTheAlbedoLayout() throws Exception {
        byte[] image=png(4,4,0x80604020);
        var data=StaticMaterialAtlasData.prepare(StaticMaterialAtlasData.Kind.NORMAL,16,8,2,
            List.of(new StaticMaterialAtlasData.Sprite(NAME,0,0,4,4,2),
                new StaticMaterialAtlasData.Sprite("mod:textures/block/absent_n.png",8,0,4,4,2)),
            StaticMaterialAtlasData.Format.LAB_PBR,name->name.equals(NAME)?Optional.of(new StaticMaterialAtlasData.Image(image,null)):Optional.empty());
        assertEquals(0x80204060,data.pixelAbgr(0,0));assertEquals(0x80204060,data.pixelAbgr(0,7));
        assertEquals(0xffff7f7f,data.pixelAbgr(0,8));assertEquals(0xffff7f7f,data.pixelAbgr(0,127));
        assertEquals(0x80204060,data.pixelAbgr(1,3));assertEquals(0xffff7f7f,data.pixelAbgr(1,4));assertEquals(640,data.byteSize());
    }
    @Test void animatedResizedAndUnrecognizedMetadataNeverBecomeAMissingMapFallback() throws Exception {
        byte[] image=png(2,2,0xffffffff);
        for(String metadata:List.of("{\"animation\":{}}","{\"texture\":{\"blur\":true}}","[]","{"," ".repeat(4097)))
            assertThrows(ShaderCompatibilityException.class,()->normal(2,2,1,List.of(SPRITE),image,metadata,StaticMaterialAtlasData.Format.LINEAR));
        assertThrows(ShaderCompatibilityException.class,()->normal(2,2,1,List.of(SPRITE),png(2,4,0xffffffff),null,StaticMaterialAtlasData.Format.LINEAR));
        assertEquals(1,normal(2,2,1,List.of(SPRITE),image,"{}",StaticMaterialAtlasData.Format.LINEAR).mapCount());
    }
    @Test void boundsDuplicateLocationsAndCorruptionRemainFailClosed() throws Exception {
        byte[] image=png(2,2,0xffffffff);
        assertThrows(ShaderCompatibilityException.class,()->normal(8192,8192,1,List.of(SPRITE),image,null,StaticMaterialAtlasData.Format.LINEAR));
        assertThrows(ShaderCompatibilityException.class,()->normal(2,2,1,List.of(SPRITE,SPRITE),image,null,StaticMaterialAtlasData.Format.LINEAR));
        assertThrows(ShaderCompatibilityException.class,()->normal(2,2,1,List.of(new StaticMaterialAtlasData.Sprite("minecraft:../outside_n.png",0,0,2,2,0)),image,null,StaticMaterialAtlasData.Format.LINEAR));
        image[image.length-1]^=1;
        assertThrows(ShaderCompatibilityException.class,()->normal(2,2,1,List.of(SPRITE),image,null,StaticMaterialAtlasData.Format.LINEAR));
    }
    @Test void normalsAndSpecularRemainDistinctNativeBindingsOnBothMeshesAndShadow() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh(V,"uniform sampler2D normals;\nuniform sampler2D specular;\n"
                +"void main(){gl_FragColor=texture2DLod(normals,vec2(.5),1.0)+texture2D(specular,vec2(.5));}",List.of(0),shadow,extended);
            assertTrue(program.usesNormals());assertTrue(program.usesSpecular());
            assertTrue(program.fragmentSource().contains("textureLod(OculusNormals,"));
            assertEquals(4,TextureBindingCapacity.nativeCounts(program).distinct());
        }
    }
    @Test void inactiveNormalDeclarationAllocatesNoAtlas() throws Exception {
        assertFalse(NativeTerrainProgram.adapt(V,"uniform sampler2D normals;\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)).usesNormals());
    }
    @Test void malformedDuplicateAndReservedNormalDeclarationsRejectEvenIfUnused() {
        for(String declaration:List.of("uniform float normals;","uniform sampler2D normals[2];","uniform sampler2D normals=0;",
            "uniform sampler2D normals;\nuniform sampler2D normals;","uniform sampler2D OculusNormals;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,declaration+"\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)),declaration);
    }
    @Test void vertexMaterialSamplingIsStillRejected() {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("uniform sampler2D normals;\nvoid main(){gl_Position=ftransform()+texture2D(normals,vec2(.5));}","void main(){gl_FragColor=vec4(1);}",List.of(0)));
    }
}
