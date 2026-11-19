package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import org.junit.jupiter.api.Test;

final class NativeClockProbeTest {
    @Test void clockProbePreparesNativeStagesAndRealPostClockBindings() throws Exception {
        var resource = getClass().getClassLoader().getResource("clock-terrain");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "clock", "Clock probe", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("float(oculusNative_worldTime)"));
                assertTrue(source.contains("float(oculusNative_worldDay)"));
            }
        }
        var semantics = prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.WORLD_TIME));
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.WORLD_DAY));
    }
}
