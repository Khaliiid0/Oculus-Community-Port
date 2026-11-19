package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeSpecularTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="uniform sampler2D specular;\nvoid main(){gl_FragColor=texture2DLod(specular,vec2(.5),1000.0);}";
    @Test void staticSpecularUsesOneSeparateNativeBindingForTerrainAndShadowInBothMeshes() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh(V,F,List.of(0),shadow,extended);
            assertTrue(program.usesSpecular());assertFalse(program.usesNoise());
            assertTrue(program.fragmentSource().contains("textureLod(OculusSpecular,"));
            assertTrue(program.colorInputs().isEmpty());
        }
    }
    @Test void invalidOrDuplicateAndReservedSpecularDeclarationsRejectEvenWhenUnused() {
        for(String declaration:List.of("uniform float specular;","uniform sampler2D specular[2];",
            "uniform sampler2D specular=0;","uniform sampler2D specular;\nuniform sampler2D specular;",
            "uniform sampler2D OculusSpecular;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
                declaration+"\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)));
    }
    @Test void inactiveSpecularDoesNotRequestAMaterialTexture() throws Exception {
        var program=NativeTerrainProgram.adapt(V,"uniform sampler2D specular;\nvoid main(){gl_FragColor=vec4(1);}",List.of(0));
        assertFalse(program.usesSpecular());
    }
    @Test void vertexMaterialSamplingRemainsExplicitlyUnsupported() {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("uniform sampler2D specular;\n"
            +V.replace("ftransform()","ftransform()+texture2D(specular,vec2(.5))"),F,List.of(0)));
    }
    @Test void diagnosticFixturePreparesNativeTerrainShadowAndTwoPostInputs() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-specular").toURI());
        var result=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("specular","Specular",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=result.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());assertTrue(world.nativeShadow().usesSpecular());
        for(var layer:TerrainLayerPlan.Layer.values())assertTrue(world.terrainLayers().program(layer).nativeProgram().usesSpecular());
        assertEquals(2,result.programs().getFirst().fragment().samplers().size());
        assertTrue(result.images().isEmpty());
    }
}
