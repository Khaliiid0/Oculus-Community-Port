package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import dev.oculusforge.shaderpack.GlslPreprocessor;
import org.junit.jupiter.api.Test;

final class NativeShaderExtensionsTest {
    @Test void actualLodFixturePreparesBothNativeStagesWithoutExtensionDirectives() throws Exception {
        var resource = getClass().getClassLoader().getResource("lod-terrain");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("lod", "LOD probe", java.nio.file.Path.of(resource.toURI()),
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertFalse(program.fragmentSource().contains("#extension"));
            assertTrue(program.fragmentSource().contains("textureGrad(Sampler0"));
            assertTrue(program.fragmentSource().contains("textureProjGrad(Sampler0"));
        }
    }
    private static final String V = "varying vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }";
    private static final String F = "uniform sampler2D texture;\nvarying vec2 uv;\nvoid main() { gl_FragColor = %s; }";

    @Test void onlyTheMappedTextureLodDirectiveIsConsumed() throws Exception {
        for (String behavior : List.of("enable", "require", "warn")) {
            String directive = "  # extension GL_ARB_shader_texture_lod : " + behavior;
            String source = directive + "\nfloat unchanged;\n";
            assertEquals(" ".repeat(directive.length()) + "\nfloat unchanged;\n", NativeShaderExtensions.adapt(source));
        }
    }

    @Test void mapsOnlyCompleteFunctionNamesAndLeavesArgumentsUntouched() throws Exception {
        String source = "vec4 result = texture2DGradARB(tex, uv, dx, dy);\n"
            + "float texture2DGradARBScale;\nvec4 projected = texture2DProjLod(tex, vec3(uv, 2.0), lod);";
        assertEquals("vec4 result = textureGrad(tex, uv, dx, dy);\n"
            + "float texture2DGradARBScale;\nvec4 projected = textureProjLod(tex, vec3(uv, 2.0), lod);",
            NativeShaderExtensions.adapt(source));
    }

    @Test void nativeTerrainAndShadowSamplingMatchesTheCoreEquivalent() throws Exception {
        var calls = List.of(
            new String[] {"texture2DLod(texture, uv, 0.0)", "textureLod(texture, uv, 0.0)"},
            new String[] {"texture2DProjLod(texture, vec3(uv, 1.0), 0.0)", "textureProjLod(texture, vec3(uv, 1.0), 0.0)"},
            new String[] {"texture2DGradARB(texture, uv, dFdx(uv), dFdy(uv))", "textureGrad(texture, uv, dFdx(uv), dFdy(uv))"},
            new String[] {"texture2DProjGradARB(texture, vec3(uv, 1.0), dFdx(uv), dFdy(uv))", "textureProjGrad(texture, vec3(uv, 1.0), dFdx(uv), dFdy(uv))"});
        for (var call : calls) for (boolean shadow : new boolean[] {false, true}) {
            var legacy = NativeTerrainProgram.adaptWithMesh(V,
                "#extension GL_ARB_shader_texture_lod : enable\n" + F.formatted(call[0]), List.of(0), shadow, false);
            var core = NativeTerrainProgram.adaptWithMesh(V, F.formatted(call[1]), List.of(0), shadow, false);
            assertEquals(core.vertexSource(), legacy.vertexSource());
            assertEquals(core.fragmentSource().replaceAll("\\s+", ""), legacy.fragmentSource().replaceAll("\\s+", ""));
            assertFalse(legacy.fragmentSource().contains("#extension"));
            assertTrue(legacy.fragmentSource().contains("Sampler0"));
        }
    }

    @Test void unmappedExtensionsBehaviorsAndMalformedDirectivesFailClosed() {
        for (String directive : List.of("GL_ARB_shader_image_load_store : require", "GL_ARB_gpu_shader5 : require",
            "all : warn", "all : disable", "GL_ARB_shader_texture_lod : disable",
            "GL_ARB_shader_texture_lod : enabled", "GL_ARB_shader_texture_lod", "GL_ARB_shader_texture_lod : enable junk")) {
            var error = assertThrows(ShaderCompatibilityException.class,
                () -> NativeShaderExtensions.adapt("#extension " + directive + "\n"));
            assertTrue(error.getMessage().contains("not mapped"));
        }
    }

    @Test void knownDirectiveDoesNotHideALaterUnsupportedDirective() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeShaderExtensions.adapt(
            "#extension GL_ARB_shader_texture_lod : enable\n# extension GL_ARB_shader_image_load_store : require\n"));
    }

    @Test void unavailableSamplerTypesAndFeedbackRemainRejected() {
        for (String uniform : List.of("uniform sampler3D texture;", "uniform sampler2D colortex0;", "uniform sampler2DShadow texture;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(V,
                "#extension GL_ARB_shader_texture_lod : enable\n" + uniform + "\nvarying vec2 uv;\n"
                    + "void main() { gl_FragColor = texture2DLod("+(uniform.contains("colortex0")?"colortex0":"texture")+", uv, 0.0); }", List.of(0)));
    }

    @Test void unmappedLegacyTextureFunctionsAreNotPassedThroughSilently() {
        for (String function : List.of("texture2DLodARB", "textureCubeGradARB", "shadow2DGradARB", "texture2DGradEXT"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeShaderExtensions.adapt(function + "(s, uv, dx, dy);"));
    }

    @Test void commentedDirectivesDoNotEnableOrBlockNativePrograms() throws Exception {
        var program = NativeTerrainProgram.adapt(V,
            "/* #extension GL_ARB_gpu_shader5 : require */\n" + F.formatted("texture2DLod(texture, uv, 0.0)"), List.of(0));
        assertTrue(program.fragmentSource().contains("textureLod(Sampler0, uv, 0.0)"));
    }

    @Test void optionalUnusedImageRequestDoesNotInventImageSupport() throws Exception {
        for(String behavior:List.of("enable","warn")) {
            String directive="#extension GL_ARB_shader_image_load_store : "+behavior;
            assertEquals(" ".repeat(directive.length())+"\nvoid main(){}",
                NativeShaderExtensions.adapt(directive+"\nvoid main(){}"));
        }
        for(String feature:List.of("uniform image2D outputImage;", "writeonly uniform uimage3D voxelimg;",
            "imageStore(voxelimg,ivec3(0),uvec4(1));", "imageLoad(voxelimg,ivec3(0));",
            "imageAtomicAdd(voxelimg,ivec3(0),1);", "memoryBarrier();", "memoryBarrierImage();",
            "layout(early_fragment_tests) in;", "coherent int value;", "int n=gl_MaxImageUnits;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeShaderExtensions.adapt(
                "#extension GL_ARB_shader_image_load_store : enable\n"+feature));
    }

    @Test void preprocessedDisabledVoxelBranchIsSafeButEnabledBranchRejects() throws Exception {
        String source="#extension GL_ARB_shader_image_load_store : enable\n#ifdef VOXELS\nwriteonly uniform uimage3D voxelimg;\n#endif\nvoid main(){}";
        assertFalse(NativeShaderExtensions.adapt(GlslPreprocessor.preprocess(source,java.util.Map.of())).contains("#extension"));
        assertThrows(ShaderCompatibilityException.class,()->NativeShaderExtensions.adapt(
            GlslPreprocessor.preprocess(source,java.util.Map.of("VOXELS","1"))));
    }
}
