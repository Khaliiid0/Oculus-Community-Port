package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeTerrainProgramTest {
    @Test void extendedProbeBothWorldProgramsPrepareOnlyWithTheirMeshInputs() throws Exception {
        String prefix = "/extended-terrain/shaders/";
        String v, f, sv, sf;
        try (var input = getClass().getResourceAsStream(prefix + "gbuffers_terrain.vsh")) { v = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        try (var input = getClass().getResourceAsStream(prefix + "gbuffers_terrain.fsh")) { f = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        try (var input = getClass().getResourceAsStream(prefix + "shadow.vsh")) { sv = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        try (var input = getClass().getResourceAsStream(prefix + "shadow.fsh")) { sf = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        assertNotNull(NativeTerrainProgram.adaptWithMesh(v, f, List.of(0), false, true));
        assertNotNull(NativeTerrainProgram.adaptWithMesh(sv, sf, List.of(0), true, true));
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithMesh(v, f, List.of(0), false, false));
    }
    @Test void extendedMeshBindsRealGeometryAndMidUv() throws Exception {
        String v = "attribute vec4 at_tangent;\nattribute vec4 mc_midTexCoord;\n" + VERTEX
            .replace("tint = gl_Color;", "tint = vec4(gl_NormalMatrix * gl_Normal, at_tangent.w); uv = mc_midTexCoord.xy;");
        var program = NativeTerrainProgram.adaptWithMesh(v, FRAGMENT, List.of(0, 7), false, true);
        assertTrue(program.vertexSource().contains("transpose(inverse(mat3(ModelViewMat))) * OculusNormal.xyz"));
        assertTrue(program.vertexSource().contains("OculusTangent.w"));
        assertTrue(program.vertexSource().contains("vec4(OculusMidUV, 0.0, 1.0).xy"));
        assertFalse(program.vertexSource().contains("attribute "));
    }

    @Test void shadowNormalUsesTheSeparateViewMatrix() throws Exception {
        var program = NativeTerrainProgram.adaptWithMesh(
            VERTEX.replace("tint = gl_Color;", "tint = vec4(gl_NormalMatrix * gl_Normal, 1);"),
            FRAGMENT.replace("gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);", ""), List.of(0), true, true);
        assertTrue(program.usesWorldMatrices());
        assertTrue(program.needsShadowMatrices());
        assertTrue(program.vertexSource().contains("transpose(inverse(mat3(oculusNative_shadowModelView)))"));
    }

    @Test void materialIdsUsePackLookupForBothWorldStages() throws Exception {
        for (boolean shadow : List.of(false, true)) {
            for (String type : List.of("vec2", "vec3", "vec4")) {
                var p = NativeTerrainProgram.adaptWithMesh("attribute " + type + " mc_Entity;\n" +
                    VERTEX.replace("tint = gl_Color;", "tint = vec4(mc_Entity.x, mc_Entity.y, 0, 1);"),
                    FRAGMENT.replace("gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);", ""), List.of(0), shadow, true);
                assertTrue(p.usesMaterialIds());
                assertTrue(p.vertexSource().contains("texelFetch(OculusMaterialIds"));
                assertTrue(p.vertexSource().contains("oculusNativeMaterial()"));
                assertFalse(p.vertexSource().contains("mc_Entity"));
            }
        }
    }

    @Test void materialDeclarationsAndReservedInputsAreValidated() {
        for (String prefix : List.of("attribute float mc_Entity;", "vec4 x = mc_Entity;",
            "attribute vec4 mc_Entity; attribute vec4 mc_Entity;", "in int OculusState;", "uniform sampler2D OculusMaterialIds;")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithMesh(
                prefix + "\n" + VERTEX, FRAGMENT, List.of(0, 7), false, true));
        }
    }

    @Test void extendedAttributesRequireCorrectAndUniqueDeclarations() {
        for (String prefix : List.of("attribute vec3 at_tangent;", "attribute vec3 mc_midTexCoord;",
            "attribute vec4 at_tangent;\nattribute vec4 at_tangent;", "vec4 x = at_tangent;")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithMesh(
                prefix + "\n" + VERTEX, FRAGMENT, List.of(0, 7), false, true));
        }
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptWithMesh(
            "vec4 OculusNormal;\n" + VERTEX, FRAGMENT, List.of(0, 7), false, true));
    }

    @Test void extendedModeChangesSourceFingerprintAndSupportsVec2MidUv() throws Exception {
        var base = NativeTerrainProgram.adaptWithMesh(VERTEX, FRAGMENT, List.of(0, 7), false, false);
        var extended = NativeTerrainProgram.adaptWithMesh(VERTEX, FRAGMENT, List.of(0, 7), false, true);
        assertNotEquals(base.fingerprint(), extended.fingerprint());
        var mid = NativeTerrainProgram.adaptWithMesh("attribute vec2 mc_midTexCoord;\n" + VERTEX
            .replace("tint = gl_Color;", "tint = gl_Color; uv = mc_midTexCoord;"), FRAGMENT, List.of(0, 7), false, true);
        assertTrue(mid.vertexSource().contains("uv = OculusMidUV;"));
    }
    @TempDir Path directory;
    private static final String VERTEX = """
        #version 120
        varying vec2 uv;
        varying vec2 lm;
        varying vec4 tint;
        void main() {
            uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
            lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
            tint = gl_Color;
            gl_Position = ftransform();
        }
        """;
    private static final String FRAGMENT = """
        #version 120
        varying vec2 uv;
        varying vec2 lm;
        varying vec4 tint;
        uniform sampler2D texture;
        uniform sampler2D lightmap;
        void main() {
            vec4 value = texture2D(texture, uv) * tint;
            if (value.a < 0.1) discard;
            gl_FragData[0] = value * texture2D(lightmap, lm);
            gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);
        }
        """;

    @Test void actualMeshInputsReplaceFullscreenFabrication() throws Exception {
        var nativeProgram = NativeTerrainProgram.adapt(VERTEX, FRAGMENT, List.of(0, 7));
        assertTrue(nativeProgram.vertexSource().contains("Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset"));
        assertTrue(nativeProgram.vertexSource().contains("oculusNativeVertexProjection(ProjMat) * ModelViewMat * oculusNativeVertex"));
        assertTrue(nativeProgram.vertexSource().contains("0.03125,0.03125,0.03125,1"));
        assertFalse(nativeProgram.vertexSource().contains("gl_VertexID"));
        assertTrue(nativeProgram.fragmentSource().contains("texture(Sampler0, uv)"));
        assertTrue(nativeProgram.fragmentSource().contains("texture(Sampler2, lm)"));
        assertTrue(nativeProgram.fragmentSource().contains("discard"));
        assertTrue(nativeProgram.fragmentSource().contains("layout(location=1)"));
    }

    @Test void modernTextureFunctionIsNotRenamedWithLegacyTextureSampler() throws Exception {
        var nativeProgram = NativeTerrainProgram.adapt(VERTEX, FRAGMENT.replace("texture2D(texture,", "texture(texture,"), List.of(0, 7));
        assertTrue(nativeProgram.fragmentSource().contains("texture(Sampler0, uv)"));
        assertFalse(nativeProgram.fragmentSource().contains("Sampler0(Sampler0"));
    }

    @Test void missingGeometryAttributesAreDiagnosedRatherThanInvented() {
        for (String attribute : List.of("gl_Normal", "gl_NormalMatrix", "mc_Entity", "mc_midTexCoord", "at_tangent")) {
            var failure = assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adaptWithMesh(VERTEX + "\nvec3 required = " + attribute + ";", FRAGMENT, List.of(0, 7), false, false));
            assertTrue(failure.getMessage().contains("mesh attributes"));
        }
    }

    @Test void unsupportedVertexUniformsAndBuiltinsDoNotReachTheGpu() {
        for (String suffix : List.of("\nuniform float missingWorld;\nfloat required=missingWorld;", "\nin vec3 extra;", "\nvec4 unsupported = gl_MultiTexCoord3;")) {
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(VERTEX + suffix, FRAGMENT, List.of(0, 7)));
        }
    }

    @Test void activeFragmentUniformsRequireRealWorldBindings() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX,
            FRAGMENT.replace("void main()", "uniform float missingLightFactor;\nvoid main()").replace("value.a < 0.1", "value.a < missingLightFactor"), List.of(0, 7)));
    }

    @Test void feedbackTexturesAndUnknownSamplersAreRejected() {
        for (String name : List.of("colortex7", "depthtex0", "normalMap")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX,
                FRAGMENT.replace("lightmap", name), List.of(0, 7)));
        }
    }

    @Test void outputLocationsMustFitTheSelectedMrtLayout() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX, FRAGMENT, List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX,
            FRAGMENT.replace("gl_FragData[1]", "gl_FragData[8]"), List.of(0, 7)));
    }

    @Test void fingerprintsAreDeterministicAndTrackShaderEdits() throws Exception {
        var base = NativeTerrainProgram.adapt(VERTEX, FRAGMENT, List.of(0, 7));
        assertEquals(base.fingerprint(), NativeTerrainProgram.adapt(VERTEX, FRAGMENT, List.of(0, 7)).fingerprint());
        assertNotEquals(base.fingerprint(), NativeTerrainProgram.adapt(VERTEX,
            FRAGMENT.replace("0.25", "0.75"), List.of(0, 7)).fingerprint());
        assertEquals(64, base.fingerprint().length());
    }

    @Test void reservedNamesExtensionsAndDepthWritesFailClosed() {
        for (String suffix : List.of("\nfloat oculusNativeVertex;", "\n#extension GL_ARB_gpu_shader5 : require", "\nvoid extra() { gl_FragDepth = 1.0; }")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX, FRAGMENT + suffix, List.of(0, 7)));
        }
    }

    @Test void varyingTypesAreCheckedAcrossStages() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX,
            FRAGMENT.replace("varying vec2 uv;", "varying vec3 uv;"), List.of(0, 7)));
    }

    private WorldProgramPlan.Variant inspect(String vertex) throws Exception {
        Path shaders = Files.createDirectories(directory.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"), vertex);
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), "/* DRAWBUFFERS:07 */\n" + FRAGMENT);
        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            return WorldProgramPlan.inspect(pack, List.of("shaders"), ShaderPackLimits.DEFAULT, Map.of()).variant("shaders");
        }
    }

    @Test void nativeProgramSurvivesCompletedFormatAndAllocationTables() throws Exception {
        var variant = inspect(VERTEX);
        assertNotNull(variant.nativeTerrain());
        assertEquals("", variant.nativeTerrainIssue());
        var updated = variant.withColorTargets(Set.of(8)).withFormats(new ColorBufferFormats(Map.of(7, ColorBufferFormat.RGBA16F)));
        assertSame(variant.nativeTerrain(), updated.nativeTerrain());
        assertEquals(List.of(0, 7), updated.terrainTargets());
    }

    @Test void geometryDeclarationsFollowTheConfiguredMeshMode() throws Exception {
        var variant = inspect(VERTEX + "\nattribute vec4 at_tangent;");
        System.out.println("Configured terrain mesh: " + (TerrainMeshMode.EXTENDED ? "extended" : "default"));
        if (TerrainMeshMode.EXTENDED) {
            assertNotNull(variant.nativeTerrain(), variant.nativeTerrainIssue());
            assertTrue(variant.nativeTerrain().vertexSource().contains("in vec4 OculusTangent;"));
        } else {
            assertNull(variant.nativeTerrain());
            assertTrue(variant.nativeTerrainIssue().contains("mesh attributes"));
        }
    }

    @Test void distinctWaterProgramCannotBeSilentlyReplacedByNativeTerrain() throws Exception {
        Path shaders = Files.createDirectories(directory.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_water.fsh"), FRAGMENT);
        var variant = inspect(VERTEX);
        assertNull(variant.nativeTerrain());
        assertTrue(variant.nativeTerrainIssue().contains("per-layer"));
    }

    @Test void actualNativeFixtureIsPreparedRatherThanReplaced() throws Exception {
        var resource = getClass().getClassLoader().getResource("native-terrain");
        assertNotNull(resource);
        var pipeline = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "native", "Native terrain", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var world = pipeline.world().variant("shaders");
        assertNotNull(world.nativeTerrain());
        assertNotNull(world.nativeShadow());
        assertFalse(world.nativeTerrain().fragmentSource().contains("RGBA16F"), "Format metadata must not reach driver compilation");
        assertEquals(List.of(0, 7), world.terrainTargets());
        assertEquals(Set.of(7), pipeline.variants().get("shaders").buffers().borrowedColorFormats().keySet());
    }

    @Test void formatMetadataIsConsumedBeforeBothNativeShaderStagesCompile() throws Exception {
        var program = NativeTerrainProgram.adapt("const int colortex7Format = RGBA16F;\n" + VERTEX,
            "const int colortex7Format = RGBA16F;\n" + FRAGMENT, List.of(0, 7));
        assertFalse(program.vertexSource().contains("RGBA16F"));
        assertFalse(program.fragmentSource().contains("RGBA16F"));
    }

    @Test void usingMetadataAsShaderExpressionFailsWithAnExplanation() {
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(VERTEX,
            "const int colortex7Format = RGBA16F;\n" + FRAGMENT.replace("0.25", "float(colortex7Format)"), List.of(0, 7)));
    }

    @Test void nativeShadowUsesCombinedChunkTransformNotTheCameraProjection() throws Exception {
        var shadow = NativeTerrainProgram.adaptShadow(VERTEX,
            FRAGMENT.replace("gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);", ""), List.of(0));
        assertTrue(shadow.vertexSource().contains("(ModelViewMat * oculusNativeVertex)"));
        assertFalse(shadow.vertexSource().contains("ProjMat * ModelViewMat"));
    }

    @Test void nativeShadowBindsSeparateMatricesAndBothColorTargets() throws Exception {
        assertEquals(List.of(0, 1), NativeTerrainProgram.adaptShadow(VERTEX,
            FRAGMENT, List.of(0, 1)).outputTargets());
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adaptShadow(VERTEX,
            FRAGMENT, List.of(0, 2)));
        var program = NativeTerrainProgram.adaptShadow(
            VERTEX.replace("ftransform()", "gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex"),
            FRAGMENT.replace("gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);", ""), List.of(0));
        assertTrue(program.vertexSource().contains("oculusNative_shadowProjection * oculusNative_shadowModelView * oculusNativeVertex"));
        assertTrue(program.usesWorldMatrices());
    }

    @Test void shadowOnlyPackHasNoInventedNativeTerrainOrAuxiliaryTargets() throws Exception {
        var resource = getClass().getClassLoader().getResource("native-shadow-only");
        assertNotNull(resource);
        var pipeline = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "shadow", "Shadow only", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var world = pipeline.world().variant("shaders");
        assertTrue(world.terrainProgram().isBlank());
        assertNull(world.nativeTerrain());
        assertNotNull(world.nativeShadow());
        assertTrue(world.initialColorBuffers().isEmpty());
    }
}
