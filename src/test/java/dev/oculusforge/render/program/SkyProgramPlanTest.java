package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SkyProgramPlanTest {
    @TempDir Path directory;
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragData[0] = vec4(1); }";
    private void write(String name, String source) throws Exception {
        Path file = directory.resolve(name); Files.createDirectories(file.getParent()); Files.writeString(file, source);
    }
    private void sky(String root) throws Exception {
        write(root + "/gbuffers_skytextured.vsh", V);
        write(root + "/gbuffers_skytextured.fsh", F);
    }
    private WorldProgramPlan inspect() throws Exception {
        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            return WorldProgramPlan.inspect(pack, PostProcessCapabilities.ROOTS, ShaderPackLimits.DEFAULT, Map.of());
        }
    }
    @Test void skyOnlyPackRetainsPlansWithoutInventingTerrain() throws Exception {
        sky("shaders/world1");
        var plan = inspect();
        assertTrue(plan.variants().isEmpty());
        assertEquals(Set.of("shaders/world1"), plan.skyVariants().keySet());
        assertNotNull(plan.skyVariants().get("shaders/world1").programs().get("gbuffers_skytextured").prepared());
    }
    @Test void absentDimensionPairFallsBackAsAUnit() throws Exception {
        sky("shaders");
        var program = inspect().skyVariants().get("shaders/world1").programs().get("gbuffers_skytextured");
        assertEquals("shaders", program.sourceRoot()); assertNotNull(program.prepared());
    }
    @Test void partialOverrideDoesNotMixDimensionAndBaseSources() throws Exception {
        sky("shaders"); write("shaders/world1/gbuffers_skytextured.fsh", F);
        var program = inspect().skyVariants().get("shaders/world1").programs().get("gbuffers_skytextured");
        assertEquals("shaders/world1", program.sourceRoot()); assertNull(program.prepared());
        assertTrue(program.issue().contains("Missing paired"));
    }
    @Test void laterWorldTransformsPreserveSkyPlans() throws Exception {
        sky("shaders"); var plan = inspect();
        assertEquals(plan.skyVariants(), plan.withRequiredColorBuffers(Map.of()).withFormats(Map.of()).skyVariants());
        assertThrows(UnsupportedOperationException.class, () -> plan.skyVariants().clear());
    }
    @Test void skyAndPostShareSlotsIncludingSkyOnlyCustomUniforms() throws Exception {
        sky("shaders");
        write("shaders/shaders.properties", "uniform.float.aSky=2\nuniform.float.zPost=3\n");
        write("shaders/gbuffers_skytextured.fsh", "uniform float aSky;\n" + F.replace("vec4(1)", "vec4(aSky)"));
        write("shaders/final.fsh", "uniform float zPost;\n" + F.replace("vec4(1)", "vec4(zPost)"));
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "sky", "sky", directory, ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(Map.of("aSky",0,"zPost",1), prepared.variants().get("shaders").customUniforms().slots());
        var sky = prepared.world().skyVariants().get("shaders").programs().get("gbuffers_skytextured");
        assertNotNull(sky.prepared(), sky.issue());
        assertTrue(sky.prepared().fragmentSource().contains("oculusNative_oculusCustomValue0"));
        assertTrue(prepared.programs().getFirst().fragment().source().contains("oculusCustomValue1"));
    }
}
