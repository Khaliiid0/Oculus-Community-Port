package dev.oculusforge.shaderpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oculusforge.render.program.LegacyFinalShaderAdapter;
import dev.oculusforge.render.program.PackProgramCompiler;
import dev.oculusforge.render.program.PreparedPackPipeline;
import dev.oculusforge.render.program.ShaderCompatibilityException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ShaderCompatibilityRegressionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preprocessingKeepsOnlyTheActiveShaderBranch() throws Exception {
        String source = """
            #version 120
            #ifdef FRAGMENT_SHADER
            uniform float viewWidth;
            #else
            uniform mat4 unsupportedVertexMatrix;
            #endif
            """;

        String result = GlslPreprocessor.preprocess(source, Map.of("FRAGMENT_SHADER", ""));

        assertTrue(result.lines().findFirst().orElseThrow().matches("#version\\s+120"));
        assertTrue(result.contains("viewWidth"));
        assertFalse(result.contains("unsupportedVertexMatrix"));
    }

    @Test
    void reservedPreprocessorMarkersAreRejected() {
        ShaderPackException exception = assertThrows(
            ShaderPackException.class,
            () -> GlslPreprocessor.preprocess("#warning OCULUS_JCPP_GLSL_VERSION 120", Map.of())
        );
        assertEquals(ShaderPackException.Code.PREPROCESSING_FAILED, exception.code());
    }

    @Test
    void adapterHandlesLegacyAttachmentAliasesAndUniformLists() throws Exception {
        String source = """
            #version 120
            varying vec2 texcoord;
            uniform sampler2D colortex3;
            uniform sampler2D colortex0;
            uniform sampler2D normals;
            uniform float viewWidth, viewHeight;
            uniform bool heavyFog = false;
            void main() {
                vec4 color = texture2D(colortex3, texcoord);
                color += texture2D(colortex0, texcoord) * 0.0;
                if (heavyFog) color.rgb *= 0.5;
                gl_FragColor = color + vec4((viewWidth + viewHeight) * 0.0);
            }
            """;

        LegacyFinalShaderAdapter.AdaptedShader result = new LegacyFinalShaderAdapter().adapt(source);

        assertEquals(2, result.samplers().size());
        assertEquals(2, result.uniforms().size());
        assertTrue(result.source().contains("const bool heavyFog = false;"));
        assertFalse(result.source().contains("uniform sampler2D normals"));
    }

    @Test
    void unknownUsedSamplerStillFailsClosed() {
        String source = """
            #version 120
            varying vec2 texcoord;
            uniform sampler2D remoteTexture;
            void main() { gl_FragColor = texture2D(remoteTexture, texcoord); }
            """;

        assertThrows(ShaderCompatibilityException.class, () -> new LegacyFinalShaderAdapter().adapt(source));
    }

    @Test
    void opaqueWorldAndShadowSnapshotsRemainDistinct() throws Exception {
        LegacyFinalShaderAdapter.AdaptedShader result = new LegacyFinalShaderAdapter().adapt("""
            #version 120
            uniform sampler2D depthtex0;
            uniform sampler2D depthtex1;
            uniform sampler2D shadowtex0;
            uniform sampler2D shadowtex1;
            void main() {
                gl_FragColor = texture2D(depthtex0, vec2(0.5)) + texture2D(depthtex1, vec2(0.5))
                    + texture2D(shadowtex0, vec2(0.5)) + texture2D(shadowtex1, vec2(0.5));
            }
            """);

        assertEquals(4, result.samplers().size());
        assertTrue(result.source().contains("depthtex1"));
        assertTrue(result.source().contains("shadowtex1"));
    }

    @Test
    void legacyFogUsesExplicitRuntimeUniforms() throws Exception {
        LegacyFinalShaderAdapter.AdaptedShader result = new LegacyFinalShaderAdapter().adapt("""
            #version 120
            void main() { gl_FragColor = vec4(gl_Fog.color.rgb * gl_Fog.scale, 1.0); }
            """);

        assertTrue(result.source().contains("oculusLegacyFogColor"));
        assertTrue(result.source().contains("1.0 / (oculusLegacyFogEnd - oculusLegacyFogStart)"));
        assertEquals(3, result.uniforms().size());
    }

    @Test
    void worldZeroProgramIsPreferredAndPrepared() throws Exception {
        Path world = Files.createDirectories(temporaryDirectory.resolve("shaders/world0"));
        Files.writeString(world.resolve("final.fsh"), """
            #version 120
            #define FRAGMENT_SHADER
            #ifdef FRAGMENT_SHADER
            varying vec2 texcoord;
            uniform sampler2D colortex0;
            void main() { gl_FragColor = texture2D(colortex0, texcoord); }
            #endif
            """);
        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
            "test", "Test pack", temporaryDirectory, ShaderPackDescriptor.Kind.DIRECTORY
        );

        PreparedPackPipeline result = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(descriptor);

        assertEquals(1, result.programs().size());
        assertEquals("final", result.programs().getFirst().stageName());
        assertEquals("colortex0", result.programs().getFirst().fragment().samplers().getFirst().name());
    }

    @Test
    void includeCyclesAreRejected() throws Exception {
        Path shaders = Files.createDirectories(temporaryDirectory.resolve("shaders"));
        Files.writeString(shaders.resolve("a.glsl"), "#include \"b.glsl\"");
        Files.writeString(shaders.resolve("b.glsl"), "#include \"a.glsl\"");

        try (PackFileSystem pack = new DirectoryPackFileSystem(temporaryDirectory, ShaderPackLimits.DEFAULT)) {
            ShaderPackException exception = assertThrows(
                ShaderPackException.class,
                () -> new ShaderSourceLoader(pack, ShaderPackLimits.DEFAULT).load(VirtualPath.parse("shaders/a.glsl"))
            );
            assertEquals(ShaderPackException.Code.INCLUDE_CYCLE, exception.code());
        }
    }
}
