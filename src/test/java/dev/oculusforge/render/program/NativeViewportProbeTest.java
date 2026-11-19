package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import org.junit.jupiter.api.Test;

final class NativeViewportProbeTest {
    @Test void probePreparesNativeViewportBindingsInAllFourWorldStages() throws Exception {
        var resource = getClass().getClassLoader().getResource("viewport-terrain");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "viewport", "Viewport probe", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            assertFalse(program.needsShadowMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains(NativeWorldBindings.block()));
                assertTrue(source.contains("vec3(oculusNative_viewWidth, oculusNative_viewHeight, oculusNative_aspectRatio)"));
            }
        }
    }
}
