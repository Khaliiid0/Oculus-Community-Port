package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class PostUniformLayoutTest {
    private String adapt(String declarations, String expression) throws Exception {
        return new LegacyFinalShaderAdapter().adapt(declarations
            + "\nvoid main() { gl_FragColor = vec4(" + expression + "); }").source();
    }

    @Test void vec3FollowedByIntegerPreservesCpuPadding() throws Exception {
        assertTrue(adapt("uniform vec3 cameraPosition;\nuniform int frameCounter;", "cameraPosition.x + float(frameCounter)").contains(
            "vec3 cameraPosition;\n    float oculus_UniformPadding_cameraPosition;\n    int frameCounter;"));
    }

    @Test void vec3FollowedByFloatPreservesCpuPadding() throws Exception {
        assertTrue(adapt("uniform vec3 fogColor;\nuniform float rainStrength;", "fogColor.x + rainStrength").contains(
            "vec3 fogColor;\n    float oculus_UniformPadding_fogColor;\n    float rainStrength;"));
    }

    @Test void integerVectorAlsoConsumesFourLanes() throws Exception {
        assertTrue(adapt("uniform ivec3 cameraPositionInt;\nuniform int worldTime;", "float(cameraPositionInt.x + worldTime)").contains(
            "ivec3 cameraPositionInt;\n    float oculus_UniformPadding_cameraPositionInt;\n    int worldTime;"));
    }

    @Test void sharedStageBlockIsIdenticalAndReplacementIsIdempotent() throws Exception {
        var fragment = new LegacyFinalShaderAdapter().adapt("uniform vec3 cameraPosition;\n"
            + "uniform vec3 fogColor;\nuniform int frameCounter;\nvoid main() { gl_FragColor = vec4(cameraPosition + fogColor, float(frameCounter)); }");
        String vertex = LegacyFinalShaderAdapter.withUniformBlock("#version 330 core\nvoid main() {}", fragment.uniforms());
        int start = fragment.source().indexOf("layout(std140)");
        String block = fragment.source().substring(start, fragment.source().indexOf("};", start) + 2);
        assertTrue(vertex.contains(block));
        assertEquals(vertex, LegacyFinalShaderAdapter.withUniformBlock(vertex, fragment.uniforms()));
    }
}
