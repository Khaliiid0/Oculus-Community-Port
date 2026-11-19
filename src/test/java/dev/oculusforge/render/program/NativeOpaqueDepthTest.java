package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import dev.oculusforge.shaderpack.*;
import dev.oculusforge.render.world.WorldTargetLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeOpaqueDepthTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="uniform sampler2D depthtex1; void main(){gl_FragColor=texture2D(depthtex1,vec2(.5));}";

    @Test void directReadsUseIndependentDepthAndSingleReverseConversion() throws Exception {
        for (String call : List.of("texture2D(depthtex1,vec2(.5))", "texture(depthtex1,vec2(.5),0.0)",
            "textureLod(depthtex1,vec2(.5),0.0)", "texture2DLod(depthtex1,vec2(.5),0.0)",
            "texelFetch(depthtex1,ivec2(0),0)")) {
            var p=NativeTerrainProgram.adapt(V,"uniform sampler2D depthtex1; void main(){gl_FragColor="+call+";}",List.of(0));
            assertTrue(p.usesOpaqueWorldDepth());
            assertTrue(p.fragmentSource().contains("uniform sampler2D OculusOpaqueWorldDepth;"));
            assertEquals(1,p.fragmentSource().split("1.0-value.r",-1).length-1);
            assertFalse(p.fragmentSource().contains("texture2D(depthtex1"));
        }
    }

    @Test void rejectsInvalidDeclarationsAndUnsupportedUses() {
        for(String source:List.of(F.replace("sampler2D", "sampler2DShadow"),F.replace("depthtex1;","depthtex1[2];"),
            "uniform sampler2D depthtex1; "+F,F.replace("texture2D(depthtex1,vec2(.5))","helper(depthtex1)"),
            F.replace("texture2D(depthtex1,vec2(.5))","vec4(textureSize(depthtex1,0),0,1)"),
            F.replace("depthtex1","OculusOpaqueWorldDepth")))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,source,List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,F,List.of(0)));
    }

    @Test void unusedDepthDoesNotAllocate() throws Exception {
        assertFalse(NativeTerrainProgram.adapt(V,"uniform sampler2D depthtex1; void main(){gl_FragColor=vec4(1);}",List.of(0)).usesOpaqueWorldDepth());
    }

    @Test void onlyWaterMayReadOpaqueDepth(@TempDir Path root) throws Exception {
        var s=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(s.resolve("gbuffers_terrain.vsh"),V);
        Files.writeString(s.resolve("gbuffers_terrain.fsh"),"void main(){gl_FragColor=vec4(1);}");
        Files.writeString(s.resolve("gbuffers_water.vsh"),V);
        Files.writeString(s.resolve("gbuffers_water.fsh"),F);
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var w=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertTrue(w.layerRuntimeEligible());
            assertTrue(w.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram().usesOpaqueWorldDepth());
        }
        Files.writeString(s.resolve("gbuffers_terrain.fsh"),F);
        try(var fs=new DirectoryPackFileSystem(root,ShaderPackLimits.DEFAULT)) {
            var w=WorldProgramPlan.inspect(fs,List.of("shaders"),ShaderPackLimits.DEFAULT,Map.of()).variant("shaders");
            assertFalse(w.layerRuntimeEligible());
            assertNull(w.nativeTerrain());
            assertNull(w.terrainLayers().program(TerrainLayerPlan.Layer.SOLID).nativeProgram());
        }
    }

    @Test void depthMemoryCountsTowardBudget() {
        var layout=new WorldTargetLayout(854,480,Set.of(0,5),0,ColorBufferFormats.DEFAULT,true);
        assertEquals(854L*480*8,layout.byteSize());
        assertEquals(2,layout.textureCount());
        assertNotEquals(layout,new WorldTargetLayout(854,480,Set.of(0,5),0));
        assertThrows(IllegalStateException.class,()->new WorldTargetLayout(8192,8192,Set.of(),0,ColorBufferFormats.DEFAULT,true));
    }

    @Test void gpuProbeRequiresIndependentNativeAndPostSnapshots() throws Exception {
        var resource=getClass().getClassLoader().getResource("native-opaque-depth");
        assertNotNull(resource);
        var path=Path.of(resource.toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("depth-probe","Depth probe",path,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());
        var water=world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram();
        assertTrue(water.usesOpaqueWorldDepth());
        assertTrue(water.fragmentSource().contains("float surface=oculusNativeFragmentCoord().z;"));
        var samplers=prepared.programs().getLast().fragment().samplers();
        assertEquals(Set.of(0,1),samplers.stream()
            .filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.DEPTH)
            .map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex)
            .collect(java.util.stream.Collectors.toSet()));
    }
}
