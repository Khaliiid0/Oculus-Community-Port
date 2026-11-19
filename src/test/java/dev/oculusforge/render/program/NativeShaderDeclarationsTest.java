package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeShaderDeclarationsTest {
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragColor = vec4(1); }";

    @Test void expandsBslStyleMatricesVaryingsAndAttributes() throws Exception {
        assertEquals("uniform mat4 gbufferModelView;\nuniform mat4 gbufferModelViewInverse;\n"
            + "varying vec2 texCoord;\nvarying vec2 lmCoord;\nattribute vec4 mc_Entity;\nattribute vec4 at_tangent;\n",
            NativeShaderDeclarations.expandLists("uniform mat4 gbufferModelView, gbufferModelViewInverse;\n"
                + "varying vec2 texCoord, lmCoord;\nattribute vec4 mc_Entity, at_tangent;\n"));
    }

    @Test void singleDeclarationsAndExpressionsRemainByteIdentical() throws Exception {
        String source = "uniform mat4 gbufferProjection;\nuniform bool flag = false;\n"
            + "void main() { vec2 uv = vec2(1, 2); gl_Position = vec4(uv, 0, 1); }";
        assertEquals(source, NativeShaderDeclarations.expandLists(source));
    }

    @Test void multilineListsAndUnderscoreNamesWork() throws Exception {
        assertEquals("varying vec3 normal;\nvarying vec3 sun_vec;\nvarying vec3 upVec;\n",
            NativeShaderDeclarations.expandLists("  varying vec3 normal,\n  sun_vec, upVec;\n"));
    }

    @Test void rejectsComplexOrMalformedListsWithoutDroppingDeclarations() {
        for (String names : List.of("a, b[2]", "a[2], b", "a, b = 1", "a = vec2(1,2)",
            "a,,b", "a,", ",a", "a, 12bad"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeShaderDeclarations.expandLists("uniform vec2 " + names + ";\n"));
    }

    @Test void groupedSourcesProduceExactlyTheSameShadersAsExpandedSources() throws Exception {
        String vertex = "uniform mat4 gbufferProjection, gbufferModelView;\n"
            + "varying vec2 texCoord, lmCoord;\n"
            + "void main() { texCoord = gl_MultiTexCoord0.xy; lmCoord = gl_MultiTexCoord1.xy;"
            + " gl_Position = gbufferProjection * gbufferModelView * gl_Vertex; }";
        String fragment = "varying vec2 texCoord, lmCoord;\nuniform sampler2D texture, lightmap;\n"
            + "void main() { gl_FragColor = texture2D(texture, texCoord) * texture2D(lightmap, lmCoord); }";
        var grouped = NativeTerrainProgram.adaptWithMesh(vertex, fragment, List.of(0), false, false);
        var expanded = NativeTerrainProgram.adaptWithMesh(NativeShaderDeclarations.expandLists(vertex),
            NativeShaderDeclarations.expandLists(fragment), List.of(0), false, false);
        assertEquals(expanded, grouped);
        assertTrue(grouped.usesWorldMatrices());
        assertTrue(grouped.fragmentSource().contains("uniform sampler2D Sampler0;"));
        assertTrue(grouped.fragmentSource().contains("uniform sampler2D Sampler2;"));
    }

    @Test void fragmentListsCannotBypassTypeDuplicateOrMissingOutputChecks() {
        String vertex = "varying vec2 a;\nvarying vec2 b;\n" + V;
        for (String declaration : List.of("varying vec3 a, b;", "varying vec2 a, c;",
            "varying vec2 a, a;", "varying vec2 a[2];", "varying mat4 a;"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(vertex, declaration + "\n" + F, List.of(0)));
    }

    @Test void groupedUniformsStillEnforceTypeDuplicateAndUnsupportedBindings() {
        for (String declaration : List.of("uniform mat4 gbufferProjection, gbufferProjection;",
            "uniform vec3 gbufferProjection, gbufferModelView;"))
            assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adapt(declaration + "\n" + V, F, List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "uniform mat4 gbufferProjection, unavailable;\n"+V.replace("ftransform()","unavailable*ftransform()"),F,List.of(0)));
    }

    @Test void groupedAttributesRetainTheExtendedMeshRequirement() throws Exception {
        String vertex = "attribute vec4 mc_Entity, at_tangent;\n" + V;
        assertThrows(ShaderCompatibilityException.class,
            () -> NativeTerrainProgram.adaptWithMesh(vertex, F, List.of(0), false, false));
        var extended = NativeTerrainProgram.adaptWithMesh(vertex, F, List.of(0), false, true);
        assertTrue(extended.usesMaterialIds());
    }

    @Test void groupedMatrixFixturesProduceTheSamePreviouslyGpuTestedPrograms() throws Exception {
        for (String fixture : List.of("matrix-terrain", "matrix-materials")) {
            var root = java.nio.file.Path.of(getClass().getClassLoader().getResource(fixture + "/shaders").toURI());
            for (String stage : List.of("gbuffers_terrain", "shadow")) {
                String vertex = java.nio.file.Files.readString(root.resolve(stage + ".vsh"));
                String fragment = java.nio.file.Files.readString(root.resolve(stage + ".fsh"));
                boolean shadow = stage.equals("shadow"), extended = fixture.equals("matrix-materials");
                var baseline = NativeTerrainProgram.adaptWithMesh(vertex, fragment, List.of(0), shadow, extended);
                String groupedVertex = groupPairs(vertex), groupedFragment = groupPairs(fragment);
                assertNotEquals(vertex, groupedVertex);
                assertEquals(baseline, NativeTerrainProgram.adaptWithMesh(groupedVertex, groupedFragment,
                    List.of(0), shadow, extended), fixture + "/" + stage);
            }
        }
    }

    private static String groupPairs(String source) {
        return source.replace("\r\n", "\n").replaceAll(
            "(?m)^(uniform|varying|attribute) (\\w+) (\\w+);\\n\\1 \\2 (\\w+);", "$1 $2 $3, $4;");
    }
}
