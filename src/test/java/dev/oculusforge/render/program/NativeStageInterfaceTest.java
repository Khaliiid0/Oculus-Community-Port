package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeStageInterfaceTest {
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "void main(){gl_FragColor=vec4(1);}";

    @Test void modernGpuFixturePreparesAllTerrainLayersAndShadowWithoutFallback() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("native-stage-interface").toURI());
        var compiler = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        var prepared = compiler.prepare(new dev.oculusforge.shaderpack.ShaderPackDescriptor("interfaces", "Interfaces", root,
            dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertEquals(List.of(0, 4), world.terrainTargets());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.vertexSource().contains("flat out int material;"));
            assertTrue(program.fragmentSource().contains("flat in int material;"));
            assertTrue(program.vertexSource().contains("supply(const in vec3 pos,out vec3 result,inout float factor)"));
            assertTrue(program.fragmentSource().contains("noperspective centroid in vec2 screenMarker;"));
        }
        assertEquals(0, compiler.cachedPipelineCount());
    }

    @Test void qualifiedFunctionsAreNotMistakenForMeshInputsInEitherWorldStage() throws Exception {
        String vertex = "void tweak(in vec3 pos, out vec3 result, inout float factor){result=pos*factor;factor=1.;}\n"
            + "void main(){vec3 r;float f=1.;tweak(vec3(1),r,f);gl_Position=ftransform()+vec4(r*f,0);}";
        for (boolean shadow : List.of(false, true)) for (boolean extended : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh(vertex, F, List.of(0), shadow, extended);
            assertTrue(program.vertexSource().contains("in vec3 pos, out vec3 result, inout float factor"));
            assertTrue(program.vertexSource().contains("tweak(vec3(1),r,f)"));
            assertFalse(program.usesWorldMatrices());
        }
    }

    @Test void modernGroupedFlatAndSmoothInterfacesMatchWithoutLosingQualifiers() throws Exception {
        String vertex = "flat out int mat;\nflat out vec3 upVec,sunVec;\nout vec2 uv;\n"
            + "void main(){mat=3;upVec=vec3(1);sunVec=vec3(0);uv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}";
        String fragment = "flat in int mat;\nflat in vec3 upVec, sunVec;\nin vec2 uv;\n"
            + "void main(){gl_FragColor=vec4(upVec+sunVec+vec3(uv,float(mat)),1);}";
        for (boolean shadow : List.of(false, true)) for (boolean extended : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh(vertex, fragment, List.of(0), shadow, extended);
            assertTrue(program.vertexSource().contains("flat out int mat;"));
            assertTrue(program.fragmentSource().contains("flat in int mat;"));
            assertTrue(program.vertexSource().contains("flat out vec3 upVec;\nflat out vec3 sunVec;"));
            assertTrue(program.fragmentSource().contains("flat in vec3 upVec;\nflat in vec3 sunVec;"));
        }
    }

    @Test void legacyAndModernFloatInterfacesInteroperateWithExplicitSmoothDefaults() throws Exception {
        for (String vertexQualifier : List.of("varying", "out", "smooth out"))
            for (String fragmentQualifier : List.of("varying", "in", "smooth in")) {
                var program = NativeTerrainProgram.adapt(vertexQualifier + " vec2 uv;\n" + V,
                    fragmentQualifier + " vec2 uv;\n" + F, List.of(0));
                assertTrue(program.vertexSource().contains("out vec2 uv;"));
                assertTrue(program.fragmentSource().contains("in vec2 uv;"));
            }
    }

    @Test void noperspectiveCentroidAndQualifierOrderArePreserved() throws Exception {
        var program = NativeTerrainProgram.adapt("centroid out noperspective vec3 value;\n" + V,
            "noperspective centroid in vec3 value;\n" + F, List.of(0));
        assertTrue(program.vertexSource().contains("noperspective centroid out vec3 value;"));
        assertTrue(program.fragmentSource().contains("noperspective centroid in vec3 value;"));
    }

    @Test void signedUnsignedIntegerScalarsAndVectorsRequireFlatOnBothSides() throws Exception {
        for (String type : List.of("int", "uint", "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4")) {
            assertNotNull(NativeTerrainProgram.adapt("flat out " + type + " value;\n" + V,
                "flat in " + type + " value;\n" + F, List.of(0)));
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("out " + type + " value;\n" + V, F, List.of(0)));
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("flat out " + type + " value;\n" + V,
                "in " + type + " value;\n" + F, List.of(0)));
        }
    }

    @Test void missingTypeInterpolationAndCentroidMismatchRejectEvenUnusedInputs() {
        for (String input : List.of("in vec3 value;", "flat in vec2 value;", "noperspective in vec2 value;",
            "centroid in vec2 value;", "in vec2 absent;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("out vec2 value;\n" + V,
                input + "\n" + F, List.of(0)), input);
    }

    @Test void duplicateDeclarationsAndDuplicateGroupedNamesRejectAcrossLegacyAndModernSyntax() {
        for (String declaration : List.of("out vec2 a,a;", "out vec2 a;\nvarying vec2 a;",
            "out vec2 a;out vec2 a;", "flat out vec3 a,b;\nout vec3 b;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(declaration + "\n" + V, F, List.of(0)), declaration);
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("out vec2 a;\n" + V,
            "in vec2 a;\nvarying vec2 a;\n" + F, List.of(0)));
    }

    @Test void arraysBlocksLayoutsMatricesInitializersAndUnsupportedTypesAreNotPretendedSupported() {
        for (String declaration : List.of("out vec2 a[2];", "out Block{vec2 a;} data;", "layout(location=0) out vec2 a;",
            "out mat3 a;", "out bool a;", "out double a;", "out vec2 a=vec2(0);", "out vec2 a,b[2];",
            "out vec2 a,,b;", "varying mat4 a;", "out vec2 a"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(declaration + "\n" + V, F, List.of(0)), declaration);
    }

    @Test void conflictingDuplicateAndWrongStageQualifiersReject() {
        for (String declaration : List.of("flat flat out vec2 a;", "flat smooth out vec2 a;", "in out vec2 a;",
            "varying out vec2 a;", "centroid centroid out vec2 a;", "sample out vec2 a;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(declaration + "\n" + V, F, List.of(0)), declaration);
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt("in vec3 realMeshInput;\n" + V, F, List.of(0)));
    }

    @Test void qualifiedPrototypesUnnamedParametersAndArraysDoNotAlterShaderSemantics() throws Exception {
        String source = "void helper(const in highp vec3, out vec3);\n"
            + "void helper(const in highp vec3 pos, out vec3 result){result=pos;}\n"
            + "void arrayHelper(in vec3 pos[2],inout vec3 result){result+=pos[0];}\n" + V;
        var parsed = NativeStageInterface.parse(source, true);
        assertEquals(source, parsed.source());
        assertEquals(source.length(), parsed.validationSource().length());
        assertEquals(source.lines().count(), parsed.validationSource().lines().count());
        assertFalse(java.util.regex.Pattern.compile("\\b(?:in|out|inout)\\b").matcher(parsed.validationSource()).find());
    }

    @Test void malformedFunctionParametersCannotMaskRealInputsOrUniforms() {
        for (String header : List.of("void helper(in out vec3 pos)", "void helper(in in vec3 pos)",
            "void helper(const out vec3 pos)", "void helper(in highp lowp vec3 pos)",
            "void helper(in uniform vec3 pos)", "void helper(in vec3 pos=vec3(0))"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeStageInterface.parse(header + "{}\n" + V, true), header);
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "void helper(){in vec3 extra;}\nvoid main(){helper();gl_Position=ftransform();}", F, List.of(0)));
    }

    @Test void diagnosticsExcludeValidFunctionParametersButRetainRealMissingUniforms() {
        String vertex = "uniform float missing;\nvec4 helper(in vec3 pos){return vec4(pos*missing,0);}\n"
            + "void main(){gl_Position=ftransform()+helper(vec3(1));}";
        var error = assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(vertex, F, List.of(0)));
        assertTrue(error.getMessage().endsWith(": missing"), error.getMessage());
        assertFalse(error.getMessage().contains("pos"));
    }

    @Test void commentsLineDirectivesAndNestedBodiesCannotCreateFakeInterfaces() throws Exception {
        String source = "#line 40 2\n// flat out int fake;\nout vec2 uv;\n"
            + "vec2 helper(in vec2 p){if(p.x>0.){return p;}return vec2(0);}\n"
            + "void main(){uv=helper(vec2(1));gl_Position=ftransform();}";
        var stage = NativeStageInterface.parse(source, true);
        assertEquals(java.util.Set.of("uv"), stage.varyings().keySet());
        assertTrue(stage.source().contains("#line 40 2"));
        assertTrue(stage.source().contains("vec2 helper(in vec2 p)"));
        assertTrue(stage.source().contains("// flat out int fake;"));
    }

    @Test void malformedScopesAndUnprocessedMacrosFailClosed() {
        for (String source : List.of("#define STAGE out\nSTAGE vec2 a;\n" + V, "out vec2 a;\nvoid main(){",
            "out vec2 a;\nvoid main()){}", "out vec2 a;\nvoid main(){}}"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeStageInterface.parse(source, true), source);
    }

    @Test void interfaceCountAndReservedNamesAreBounded() {
        var source = new StringBuilder();
        for (int i = 0; i < 65; i++) source.append("out vec2 v").append(i).append(";\n");
        source.append(V);
        var error = assertThrows(ShaderCompatibilityException.class, () -> NativeStageInterface.parse(source.toString(), true));
        assertTrue(error.getMessage().contains("64 stage-interface"));
        for (String name : List.of("gl_fake", "OculusFake", "oculus_FragColor0", "Position", "Color", "ModelViewMat", "Sampler0"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeStageInterface.parse("out vec2 " + name + ";\n" + V, true));
    }

    @Test void explicitFragmentOutputsRemainAvailableToTheExistingMrtValidator() throws Exception {
        var program = NativeTerrainProgram.adapt("flat out int mat;\n" + V,
            "flat in int mat;\nlayout(location=0) out vec4 color;\nvoid main(){color=vec4(float(mat));}", List.of(0));
        assertTrue(program.fragmentSource().contains("layout(location=0) out vec4 color;"));
    }
}
