package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeChunkContextTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="void main(){gl_FragColor=vec4(1);}";
    @Test void unusedOptionalVoxelDeclarationsDoNotRequireFabricatedInputs() throws Exception {
        var p=NativeTerrainProgram.adaptShadow("attribute vec3 at_midBlock;\nuniform int renderStage;\nuniform vec3 cameraPositionFract;\n"+V,F,List.of(0));
        assertFalse(p.vertexSource().contains("at_midBlock"));
        assertFalse(p.vertexSource().contains("renderStage"));
        assertFalse(p.vertexSource().contains("cameraPositionFract"));
    }
    @Test void activeOptionalInputsStillReject() {
        for(String v:List.of("attribute vec3 at_midBlock;\nvoid main(){gl_Position=vec4(at_midBlock,1);}",
            "uniform int renderStage;\nvoid main(){gl_Position=vec4(renderStage);}",
            "uniform vec3 cameraPositionFract;\nvoid main(){gl_Position=vec4(cameraPositionFract,1);}"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(v,F,List.of(0)));
    }
    @Test void invalidOrDuplicateUnusedDeclarationsAreNotHidden() {
        for(String d:List.of("attribute vec2 at_midBlock;", "attribute vec3 at_midBlock[2];",
            "attribute vec3 at_midBlock;\nattribute vec3 at_midBlock;", "uniform float renderStage;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(d+"\n"+V,F,List.of(0)));
    }
    @Test void chunkBlockEntityContextUsesAbsenceSentinel() throws Exception {
        for(boolean shadow:new boolean[]{false,true}) {
            var p=NativeTerrainProgram.adaptWithMesh("uniform int blockEntityId;\nvoid main(){gl_Position=ftransform()+vec4(blockEntityId);}",
                "uniform int blockEntityId;\nvoid main(){if(blockEntityId/100==155)discard;gl_FragColor=vec4(1);}",List.of(0),shadow,false);
            assertTrue(p.vertexSource().contains("vec4((-1))"));
            assertTrue(p.fragmentSource().contains("(-1)/100==155"));
        }
    }
    @Test void invalidBlockEntityDeclarationsReject() {
        for(String d:List.of("uniform float blockEntityId;", "uniform int blockEntityId[2];",
            "uniform int blockEntityId;\nuniform int blockEntityId;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,d+"\n"+F,List.of(0)));
    }
    @Test void complementaryLocalBlockEntityMaterialIdSurvivesNativeAdaptation() throws Exception {
        // Complementary's terrain material branch shadows the optional chunk uniform.
        String fragment="uniform int blockEntityId;\nflat in int mat;\nvoid main(){"
            +"if(blockEntityId==15500)discard;else{int blockEntityId = mat;"
            +"if(blockEntityId < 5028)gl_FragColor=vec4(float(blockEntityId));"
            +"else gl_FragColor=vec4(1);}}";
        String vertex="flat out int mat;\nvoid main(){mat=5000;gl_Position=ftransform();}";
        for(boolean shadow:List.of(false,true)) for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh(vertex,fragment,List.of(0),shadow,extended);
            assertTrue(program.fragmentSource().contains("if((-1)==15500)"));
            assertTrue(program.fragmentSource().contains("int blockEntityId = mat;"));
            assertTrue(program.fragmentSource().contains("if(blockEntityId < 5028)"));
            assertTrue(program.fragmentSource().contains("vec4(float(blockEntityId))"));
            assertFalse(program.fragmentSource().contains("int (-1)"));
        }
    }
    @Test void blockEntityLocalScopeRestoresTheChunkSentinelOutsideIt() throws Exception {
        String result=NativeChunkContext.adapt("uniform int blockEntityId;\nvoid main(){"
            +"int a=blockEntityId;{const int blockEntityId=5000;"
            +"{int b=blockEntityId;}int c=blockEntityId;}int d=blockEntityId;}"
            +"int helper(){return blockEntityId;}");
        assertTrue(result.contains("int a=(-1);"));
        assertTrue(result.contains("const int blockEntityId=5000;{int b=blockEntityId;}int c=blockEntityId;"));
        assertTrue(result.contains("int d=(-1);"));
        assertTrue(result.contains("int helper(){return (-1);}"));
    }
    @Test void blockEntityFieldsAndPrefixedNamesAreNotChunkUniformReferences() throws Exception {
        String result=NativeChunkContext.adapt("uniform int blockEntityId;\n"
            +"struct Data{int blockEntityId;};Data data;int blockEntityIdOther;"
            +"int f(){return data.blockEntityId+blockEntityIdOther+blockEntityId;}");
        assertTrue(result.contains("struct Data{int blockEntityId;};"));
        assertTrue(result.contains("data.blockEntityId+blockEntityIdOther+(-1)"));
    }
    @Test void ambiguousBlockEntityShadowingRejectsInsteadOfCorruptingGlsl() {
        for(String body:List.of("int f(int blockEntityId){return blockEntityId;}",
            "void f(){for(int blockEntityId=0;blockEntityId<2;blockEntityId++){}}",
            "void f(){int a,blockEntityId;}","void f(){int a=0,blockEntityId=1;}"))
            assertThrows(ShaderCompatibilityException.class,
                ()->NativeChunkContext.adapt("uniform int blockEntityId;\n"+body));
    }
}
