package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.nio.file.*;
import java.util.*;
import dev.oculusforge.shaderpack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerrainAlphaSettingsTest {
    private static final Map<String,String> ENV=Map.of("MC_VERSION","260300","OCULUS_POST_PROCESSING","1");
    private static final Set<String> NAMES=Set.of("gbuffers_terrain","gbuffers_water","shadow");
    private TerrainAlphaSettings parse(String source) throws Exception { return TerrainAlphaSettings.parse(source,ENV,"shaders/world0",NAMES); }

    @Test void lowOpacityWaterHonorsPackThresholdInsteadOfInheritedPointOne() throws Exception {
        var bsl=parse("alphaTest.gbuffers_water=GREATER 0.001");
        var complementary=parse("alphaTest.gbuffers_water=GREATER 0.0001");
        for(var settings:List.of(bsl,complementary)) {
            var test=settings.program("gbuffers_water").orElseThrow();
            assertTrue(test.accepts(.02f));assertFalse(test.accepts(test.reference()));
            assertEquals("4",settings.defines("gbuffers_water",.1f).get(TerrainAlphaSettings.MODE));
            assertFalse(settings.defines("gbuffers_water",.1f).containsKey("ALPHA_CUTOUT"));
        }
        assertFalse(new TerrainAlphaSettings.Test(TerrainAlphaSettings.Function.GREATER,.1f).accepts(.02f));
    }

    @Test void allEightFunctionsPreserveStrictBoundarySemantics() throws Exception {
        boolean[][] expected={{false,false,false},{true,false,false},{false,true,false},{true,true,false},
            {false,false,true},{true,false,true},{false,true,true},{true,true,true}};
        for(var function:TerrainAlphaSettings.Function.values()) {
            var test=parse("alphaTest.gbuffers_water="+function+" .5").program("gbuffers_water").orElseThrow();
            for(int i=0;i<3;i++)assertEquals(expected[function.ordinal()][i],test.accepts(new float[]{.25f,.5f,.75f}[i]));
        }
    }

    @Test void offIsDistinctFromMissingAndCannotLeakBetweenPrograms() throws Exception {
        var settings=parse("alphaTest.gbuffers_water=off\nalphaTest.shadow=NEVER 0");
        assertTrue(settings.program("gbuffers_water").orElseThrow().accepts(0));
        assertFalse(settings.program("shadow").orElseThrow().accepts(1));
        assertTrue(settings.program("gbuffers_terrain").isEmpty());
        assertEquals(Map.of("ALPHA_CUTOUT","0.5"),settings.defines("gbuffers_terrain",.5f));
        assertEquals(Map.of(),settings.defines("gbuffers_terrain",null));
        assertThrows(UnsupportedOperationException.class,()->settings.programs().clear());
    }

    @Test void knownConditionsDimensionOverridesAndUnrelatedSettingsAreSelectedConservatively() throws Exception {
        String source="alphaTest.gbuffers_water=GREATER .001\nalphaTest.world0/gbuffers_water=off\n"
            +"#if MC_VERSION < 12000\nalphaTest.shadow=bad\n#endif\nalphaTest.gbuffers_hand=bad";
        assertEquals(TerrainAlphaSettings.Function.ALWAYS,parse(source).program("gbuffers_water").orElseThrow().function());
        assertEquals(.001f,TerrainAlphaSettings.parse(source,ENV,"shaders/world-1",NAMES).program("gbuffers_water").orElseThrow().reference());
        for(String guard:List.of("#ifdef UNKNOWN","#if UNKNOWN > 0","#define DISTANT_HORIZONS\n#ifdef DISTANT_HORIZONS"))
            assertThrows(ShaderCompatibilityException.class,()->parse(guard+"\nalphaTest.gbuffers_water=off\n#endif"));
    }

    @Test void malformedLiveSettingsFailBeforeGpuCompilation() {
        for(String value:List.of("", "OFF", "GREATER", "GREATER NaN", "GREATER Infinity", "GREATER -.1", "GREATER 1.1", "GREATER .1 extra", "GREATER_OR_EQUAL .1"))
            assertThrows(ShaderCompatibilityException.class,()->parse("alphaTest.gbuffers_water="+value),value);
        assertThrows(ShaderCompatibilityException.class,()->parse("alphaTest.gbuffers_water.colortex0=off"));
        for(float reference:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,2})
            assertThrows(IllegalArgumentException.class,()->new TerrainAlphaSettings.Test(TerrainAlphaSettings.Function.GREATER,reference));
    }

    @Test void actualDescriptorDefinesCarryExplicitModesOrUnchangedFallbacks() throws Exception {
        for(String setting:List.of("off","GREATER .001","EQUAL .5")) {
            var alpha=parse("alphaTest.gbuffers_water="+setting);
            var builder=RenderPipeline.builder().withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oculus","alpha_test"))
                .withVertexShader("v").withFragmentShader("f")
                .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES);
            alpha.apply(builder,"gbuffers_water",.1f);
            var defines=builder.build().getShaderDefines().values();
            assertEquals(alpha.defines("gbuffers_water",.1f),defines);
        }
    }

    @Test void preparedPlansGuardCopiesAndDescriptorSignaturesRetainAlphaSettings(@TempDir Path root) throws Exception {
        Path shaders=Files.createDirectories(root.resolve("shaders"));
        for(String name:List.of("gbuffers_terrain","gbuffers_water","shadow")) {
            Files.writeString(shaders.resolve(name+".vsh"),"void main(){gl_Position=ftransform();}");
            Files.writeString(shaders.resolve(name+".fsh"),"void main(){gl_FragColor=vec4(.02);}");
        }
        Files.writeString(shaders.resolve("shaders.properties"),"alphaTest.gbuffers_water=GREATER .001\nalphaTest.shadow=off");
        TerrainLayerPlan first;
        try(var pack=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            first=WorldProgramPlan.inspect(pack,List.of("shaders"),ShaderPackLimits.DEFAULT,ENV).variant("shaders").terrainLayers();
        }
        assertTrue(first.nativeProgramsReady());
        assertEquals(first.alphaTests(),first.requireShadowProducer((NativeTerrainProgram)null).alphaTests());
        assertEquals(first.alphaTests(),first.requireShadowProducer(false).alphaTests());
        Files.writeString(shaders.resolve("shaders.properties"),"alphaTest.gbuffers_water=GREATER .1\nalphaTest.shadow=off");
        try(var pack=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var second=WorldProgramPlan.inspect(pack,List.of("shaders"),ShaderPackLimits.DEFAULT,ENV).variant("shaders").terrainLayers();
            assertNotEquals(first.descriptorSignature(ColorBufferFormats.DEFAULT),second.descriptorSignature(ColorBufferFormats.DEFAULT));
        }
    }
}
