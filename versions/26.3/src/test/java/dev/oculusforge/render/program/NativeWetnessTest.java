package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeWetnessTest {
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "void main(){gl_FragColor=vec4(1);}";

    @Test void terrainAndShadowVertexAndFragmentUseTheRealSharedWorldLane() throws Exception {
        for (boolean shadow : List.of(false, true)) for (boolean extended : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh("uniform float wetness;"+V.replace("ftransform()", "ftransform()+vec4(wetness)"),
                "uniform float wetness;"+F.replace("vec4(1)", "vec4(wetness)"), List.of(0), shadow, extended);
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("vec4(oculusNative_skyColor.w)"));
                assertTrue(source.contains(NativeWorldBindings.block()));
                assertFalse(source.contains("uniform float wetness;"));
            }
        }
    }

    @Test void unusedInvalidDeclarationsRejectInNativeAndBothPostStages() {
        for (String declaration : List.of("uniform int wetness;", "uniform vec2 wetness;", "uniform float wetness[2];",
            "uniform float wetness=0.;", "uniform float wetness;uniform float wetness;")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(declaration+V, F, List.of(0)), declaration);
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(V, declaration+F, List.of(0)), declaration);
            assertThrows(ShaderCompatibilityException.class, () -> new LegacyFinalShaderAdapter().adapt(declaration+F), declaration);
            assertThrows(ShaderCompatibilityException.class, () -> new LegacyFinalVertexAdapter().adapt(declaration+V), declaration);
        }
    }

    @Test void similarlyNamedLocalsAndStructMembersStayUntouched() throws Exception {
        var result = NativeWorldBindings.adapt("uniform float wetness;float value=wetness;"
            +"struct Probe{float wetness;};void main(){Probe p;p.wetness=1.;float wetnessScale=wetness;}");
        assertTrue(result.source().contains("float value=oculusNative_skyColor.w;"));
        assertTrue(result.source().contains("p.wetness=1."));
        assertTrue(result.source().contains("float wetnessScale=oculusNative_skyColor.w;"));
        var parameter = NativeWorldBindings.adapt(
            "uniform float wetness;float helper(float wetness){return wetness;}float next(){return wetness;}");
        assertTrue(parameter.source().contains("float helper(float wetness){return wetness;}"));
        assertTrue(parameter.source().contains("float next(){return oculusNative_skyColor.w;}"));
    }

    @Test void customOverrideOfLiveWetnessRemainsForbidden() {
        assertThrows(ShaderCompatibilityException.class, () -> CustomUniformBindings.prepare(
            NativeCustomUniforms.parse("uniform.float.wetness=0\nuniform.float.probe=wetness"),
            List.of("uniform float probe;float value=probe;")));
    }

    @Test void parameterScopesHandleQualifiersPrototypesNestedBlocksAndIndependentFunctions() throws Exception {
        String helpers="float declared(float wetness);"
            +"float a(const in float wetness){if(wetness>.1){return wetness;}return wetness;}"
            +"void b(inout float wetness){wetness+=.1;}"
            +"float c(){return wetness;}";
        var result=NativeWorldBindings.adapt("uniform float wetness;"+helpers).source();
        assertTrue(result.contains(helpers.replace("float c(){return wetness;}","float c(){return oculusNative_skyColor.w;}")));
        var unshadowed=NativeWorldBindings.adapt(
            "uniform float wetness;float prototype(float wetness);float next(){return wetness;}").source();
        assertTrue(unshadowed.endsWith("float next(){return oculusNative_skyColor.w;}"));
    }

    @Test void ambiguousParameterArraysListsLoopsAndOtherUniformScopesStillReject() {
        for(String body:List.of("float f(float wetness[2]){return wetness[0];}",
            "float f(int wetness){return float(wetness);}",
            "void f(){for(float wetness=0.;wetness<1.;wetness+=.1){}}",
            "void f(){float a=0.,wetness=1.;}"))
            assertThrows(ShaderCompatibilityException.class,()->NativeWorldBindings.adapt("uniform float wetness;"+body),body);
        assertThrows(ShaderCompatibilityException.class,()->NativeWorldBindings.adapt(
            "uniform float far;float f(float far){return far;}"));
    }
}
