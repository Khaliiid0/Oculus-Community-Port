package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import dev.oculusforge.shaderpack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeColorSamplersTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static String fragment(String name) {
        return "uniform sampler2D " + name + ";\nvoid main(){gl_FragColor=texture2D("+name+",vec2(.5));}";
    }
    @Test void auxiliaryAliasesHaveCorrectBindings() throws Exception {
        for(var e:Map.of("gdepth",1,"gnormal",2,"composite",3,"gaux1",4,"gaux2",5,"gaux3",6,"gaux4",7,"colortex15",15).entrySet()) {
            var p=NativeTerrainProgram.adapt(V,fragment(e.getKey()),List.of(0));
            assertEquals(List.of(e.getValue()),p.colorInputs());
            assertTrue(p.fragmentSource().contains("uniform sampler2D OculusColor"+e.getValue()+";"));
        }
    }
    @Test void rejectsFeedbackSceneReadsShadowReadsAndSpoofedNames() {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,fragment("gaux2"),List.of(0,5)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,fragment("colortex5"),List.of(5,0)));
        for(String name:List.of("colortex0","gcolor","colortex16","colortex05","OculusColor5"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,fragment(name),List.of(1)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,fragment("gaux2"),List.of(0)));
    }
    @Test void rejectsDuplicateAliasesAndUnsupportedSamplerTypes() {
        for(String f:List.of("uniform sampler2D gaux2;\nuniform sampler2D colortex5;\nvoid main(){gl_FragColor=texture2D(gaux2,vec2(.5))+texture2D(colortex5,vec2(.5));}",
            "uniform sampler2DShadow gaux2;\nvoid main(){gl_FragColor=vec4(1);}",
            "uniform sampler2D gaux2[2];\nvoid main(){gl_FragColor=vec4(1);}"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,f,List.of(0)));
    }
    @Test void readOnlyAuxiliaryIsAllocatedByWorldPlan(@TempDir Path root) throws Exception {
        var s=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(s.resolve("gbuffers_terrain.vsh"),V);
        Files.writeString(s.resolve("gbuffers_terrain.fsh"),fragment("gaux2"));
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var world=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertTrue(world.layerRuntimeEligible());
            assertTrue(world.worldColorTargets().contains(5));
            assertEquals(List.of(5),world.terrainLayers().program(TerrainLayerPlan.Layer.SOLID).nativeProgram().colorInputs());
        }
    }
    @Test void nativeOnlyHistoryConsumerRetainsPostProducer() throws Exception {
        var resource=getClass().getClassLoader().getResource("native-color-history");
        assertNotNull(resource);
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "native-history","Native history",Path.of(resource.toURI()),ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());
        assertTrue(world.initialColorBuffers().contains(5));
        assertFalse(world.formats().shouldClear(5));
        assertEquals(Set.of(5),prepared.variants().get("shaders").buffers().retainedColorSlots().keySet());
        assertFalse(world.terrainTargets().contains(5));
    }
}
