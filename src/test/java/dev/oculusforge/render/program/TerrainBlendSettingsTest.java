package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerrainBlendSettingsTest {
    private static final Map<String,String> ENV = Map.of("MC_VERSION","260200","OCULUS_POST_PROCESSING","1");
    private static final String ALPHA = "SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA";
    private static final Set<String> NAMES = Set.of("gbuffers_water", "gbuffers_terrain");
    @TempDir Path root;
    private TerrainBlendSettings parse(String source) throws Exception {
        return TerrainBlendSettings.parse(source, ENV, "shaders/world0", NAMES);
    }
    @Test void absentSettingsRetainOpaqueAndSeparateAlphaTranslucentDefaults() throws Exception {
        var program=parse("").program("gbuffers_water");
        assertTrue(program.target(4,false,ColorBufferFormat.RGBA16F).blendFunction().isEmpty());
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),program.target(4,true,ColorBufferFormat.RGBA16F).blendFunction());
        assertEquals(ColorBufferFormat.RGBA16F.gpu(),program.target(4,true,ColorBufferFormat.RGBA16F).format());
        assertEquals(7,program.target(4,true,ColorBufferFormat.RGB16F).writeMask());
    }
    @Test void perBufferOverridesUseLogicalIdsNotAttachmentLocation() throws Exception {
        var program=parse("blend.gbuffers_water.colortex4=off\nblend.gbuffers_water.colortex8=off").program("gbuffers_water");
        var reordered=List.of(8,0,4).stream().map(logical->program.target(logical,true,ColorBufferFormat.RGBA8)).toList();
        assertTrue(reordered.get(0).blendFunction().isEmpty());
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),reordered.get(1).blendFunction());
        assertTrue(reordered.get(2).blendFunction().isEmpty());
    }
    @Test void perBufferCanReenableDisabledProgramAndDoesNotLeakBetweenPrograms() throws Exception {
        var plan=parse("blend.gbuffers_water=off\nblend.gbuffers_water.colortex4="+ALPHA);
        assertTrue(plan.program("gbuffers_water").target(0,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),plan.program("gbuffers_water").target(4,true,ColorBufferFormat.RGBA8).blendFunction());
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),plan.program("gbuffers_terrain").target(0,true,ColorBufferFormat.RGBA8).blendFunction());
    }
    @Test void allFourBlendFactorsArePreservedIndependently() throws Exception {
        var mode=parse("blend.gbuffers_water=ONE ZERO DST_ALPHA ONE_MINUS_DST_ALPHA").program("gbuffers_water")
            .target(0,true,ColorBufferFormat.RGBA8).blendFunction().orElseThrow();
        assertEquals(new BlendFunction(BlendFactor.ONE,BlendFactor.ZERO,BlendFactor.DST_ALPHA,BlendFactor.ONE_MINUS_DST_ALPHA),mode);
    }
    @Test void scopedOverridesBeatBaseWithoutContaminatingAnotherDimension() throws Exception {
        String source="blend.gbuffers_water=off\nblend.world0/gbuffers_water="+ALPHA
            +"\nblend.world0/gbuffers_water.colortex4=off\nblend.world1/gbuffers_water.colortex0=garbage";
        var world0=parse(source).program("gbuffers_water");
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),world0.target(0,true,ColorBufferFormat.RGBA8).blendFunction());
        assertTrue(world0.target(4,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
        var nether=TerrainBlendSettings.parse(source,ENV,"shaders/world-1",NAMES).program("gbuffers_water");
        assertTrue(nether.target(0,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
    }
    @Test void knownInactiveBranchesAndUnrelatedProgramsDoNotRejectTerrain() throws Exception {
        var plan=parse("#if MC_VERSION < 12000\nblend.gbuffers_water=invalid\n#endif\nblend.gbuffers_entities=invalid\n"
            +"#ifdef UNKNOWN\nblend.gbuffers_hand=invalid\n#endif\nblend.gbuffers_water.colortex4=off");
        assertTrue(plan.program("gbuffers_water").target(4,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
    }
    @Test void actualNoDhCompilerEnvironmentMatchesGlslUndefinedIntegrationMacro() throws Exception {
        assertEquals("blend.gbuffers_water.colortex4=off\n",CustomUniformProperties.select(
            "#ifdef DISTANT_HORIZONS\nblend.gbuffers_water="+ALPHA+"\n#endif\nblend.gbuffers_water.colortex4=off",ENV));
        assertTrue(parse("#ifdef DISTANT_HORIZONS\nblend.gbuffers_water=invalid\n#endif").programs().isEmpty());
        assertThrows(ShaderCompatibilityException.class,()->TerrainBlendSettings.parse(
            "#ifdef DISTANT_HORIZONS\nblend.gbuffers_water=off\n#endif",Map.of(),"shaders",NAMES));
    }
    @Test void unknownOptionsAndLocalMacroMutationsCannotGuessRenderState() {
        for(String guard:List.of("#ifdef UNKNOWN","#if UNKNOWN > 0"))
            assertThrows(ShaderCompatibilityException.class,()->parse(guard+"\nblend.gbuffers_water=off\n#endif"));
        assertThrows(ShaderCompatibilityException.class,()->parse(
            "#define DISTANT_HORIZONS\n#ifdef DISTANT_HORIZONS\nblend.gbuffers_water=off\n#endif"));
    }
    @Test void malformedRelevantSettingsFailBeforeGpuCompilation() {
        for(String value:List.of("", "OFF", "on", "SRC_ALPHA ONE_MINUS_SRC_ALPHA", "ONE ZERO ONE ZERO EXTRA",
            "CONSTANT_COLOR ONE ONE ZERO", "1 0 1 0", "ONE SRC_ALPHA_SATURATE ONE ZERO"))
            assertThrows(ShaderCompatibilityException.class,()->parse("blend.gbuffers_water="+value),value);
        for(String suffix:List.of("colortex16","colortex-1","colortex04","nonsense"))
            assertThrows(ShaderCompatibilityException.class,()->parse("blend.gbuffers_water."+suffix+"=off"));
    }
    @Test void propertiesWhitespaceEscapesAndContinuationUseJavaPropertySemantics() throws Exception {
        var plan=parse("blend.gbuffers_water.colortex4 : off\nblend.gbuffers_water=SRC_ALPHA "
            + '\\' + "\n ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA");
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),plan.program("gbuffers_water").target(0,true,ColorBufferFormat.RGBA8).blendFunction());
        assertTrue(plan.program("gbuffers_water").target(4,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
    }
    @Test void immutablePlansAndCanonicalSignaturesTrackOnlyStateChanges() throws Exception {
        var first=parse("blend.gbuffers_water.colortex4=off\nblend.gbuffers_water.colortex8=off");
        var second=parse("blend.gbuffers_water.colortex8=off\nblend.gbuffers_water.colortex4=off");
        assertEquals(first.program("gbuffers_water").signature(),second.program("gbuffers_water").signature());
        assertNotEquals(first.program("gbuffers_water").signature(),TerrainBlendSettings.Program.INHERIT.signature());
        assertThrows(UnsupportedOperationException.class,()->first.programs().clear());
        assertThrows(UnsupportedOperationException.class,()->first.program("gbuffers_water").buffers().clear());
        assertThrows(IllegalArgumentException.class,()->first.program("gbuffers_water").target(16,true,ColorBufferFormat.RGBA8));
    }
    private TerrainLayerPlan inspect() throws Exception {
        try(var pack=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            return WorldProgramPlan.inspect(pack,List.of("shaders"),ShaderPackLimits.DEFAULT,ENV).variant("shaders").terrainLayers();
        }
    }
    @Test void realPlanningCopiesAndDescriptorKeysPreserveBlendState() throws Exception {
        Path shaders=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"),"void main(){gl_Position=ftransform();}");
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),"void main(){gl_FragColor=vec4(1);}");
        Files.writeString(shaders.resolve("gbuffers_water.vsh"),"void main(){gl_Position=ftransform();}");
        Files.writeString(shaders.resolve("gbuffers_water.fsh"),"/* DRAWBUFFERS:40 */\nvoid main(){gl_FragData[0]=vec4(1);gl_FragData[1]=vec4(1);}");
        var old=inspect();
        Files.writeString(shaders.resolve("shaders.properties"),"blend.gbuffers_water.colortex4=off");
        var updated=inspect();
        assertNotEquals(old.descriptorSignature(ColorBufferFormats.DEFAULT),updated.descriptorSignature(ColorBufferFormats.DEFAULT));
        assertSame(updated.blending(),updated.requireShadowProducer(false).blending());
        assertSame(updated.blending(),updated.requireShadowProducer((NativeTerrainProgram)null).blending());
        assertTrue(updated.blending().program("gbuffers_water").target(4,true,ColorBufferFormat.RGBA8).blendFunction().isEmpty());
        Files.writeString(shaders.resolve("shaders.properties"),
            "blend.gbuffers_water=ONE ONE ONE ONE\nblend.gbuffers_water.colortex4="+ALPHA);
        assertTrue(assertThrows(ShaderCompatibilityException.class,this::inspect).getMessage().contains("pipeline descriptors"));
    }
    @Test void actualProbePreparesReorderedOutputsAndBothLogicalOverrides() throws Exception {
        Path probe=Path.of(getClass().getClassLoader().getResource("terrain-blend").toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("terrain-blend","Terrain blend",probe,ShaderPackDescriptor.Kind.DIRECTORY));
        var plan=prepared.world().variant("shaders").terrainLayers();
        assertTrue(plan.nativeProgramsReady());
        assertEquals(List.of(4,0,8),plan.program(TerrainLayerPlan.Layer.TRANSLUCENT).targets());
        assertEquals(Set.of(4,8),plan.blending().program("gbuffers_water").buffers().keySet());
        assertTrue(prepared.programs().stream().anyMatch(p->p.fragment().source().contains("vec4(1.0,0.5,0.0,0.25)")));
    }
}
