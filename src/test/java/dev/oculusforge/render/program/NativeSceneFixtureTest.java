package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import dev.oculusforge.shaderpack.*;

class NativeSceneFixtureTest {
    @Test void fixturePreparesNativeSceneAndShadowAndIndependentPostInputs() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("native-scene").toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("scene","Scene",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertEquals(java.util.List.of(0,1),world.shadowTargets());
        assertTrue(world.nativeTerrain().usesWorldMatrices());
        var semantics=prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.SCREEN_BRIGHTNESS));
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.SUN_ANGLE));
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.BLIND_FACTOR));
        assertTrue(semantics.contains(LegacyFinalShaderAdapter.UniformSemantic.MAX_BLINDNESS_DARKNESS));
    }
}
