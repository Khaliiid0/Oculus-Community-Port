package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LegacyFogAccessTest {
    @Test void nativeFieldsUseRealRangeAndSeparateAlphaWithoutTheSceneAngleLane() throws Exception {
        var result=LegacyFogAccess.adapt("vec4 a=gl_Fog . color; float x=gl_Fog.start+gl_Fog.end+gl_Fog.scale;",true);
        assertTrue(result.used());assertTrue(result.source().contains("vec4(oculusNative_fogColor.xyz, oculusNative_fogAlpha)"));
        assertTrue(result.source().contains("1.0 / (oculusNative_fogEnd - oculusNative_fogStart)"));
        assertFalse(result.source().contains("oculusNative_fogColor.w"));assertFalse(result.source().contains("max("));
    }
    @Test void postFieldsHaveIndependentHostNamesAndIdenticalReciprocalSemantics() throws Exception {
        var result=LegacyFogAccess.adapt("vec3 c=gl_Fog.color.rgb; float x=(gl_Fog.end-gl_Fog.start)*gl_Fog.scale;",false);
        assertTrue(result.source().contains("(oculusLegacyFogColor).rgb"));
        assertTrue(result.source().contains("1.0 / (oculusLegacyFogEnd - oculusLegacyFogStart)"));
        assertEquals(3,LegacyFogAccess.POST_BINDINGS.size());
    }
    @Test void prefixedLookalikesAndFogCoordinatesAreNotMemberAliases() throws Exception {
        String source="float gl_Foggy,gl_FogFragCoord; struct X{float fog;};";
        assertEquals(new LegacyFogAccess.Adapted(source,false),LegacyFogAccess.adapt(source,true));
    }
    @Test void unsupportedStructDensityAndUnknownMembersRejectRatherThanInventValues() {
        for(String source:List.of("float a=gl_Fog.density;","use(gl_Fog);","gl_Fog.unknown;","vec4 gl_Fog;","obj.gl_Fog.start;"))
            for(boolean nativeWorld:List.of(false,true))
                assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt(source,nativeWorld),source);
    }
    @Test void directStoresAndIncrementsRejectButComparisonsRemainReads() throws Exception {
        for(String source:List.of("gl_Fog.start=4;","gl_Fog.end+=2;","gl_Fog.color.rgb*=.5;","++gl_Fog.start;","gl_Fog.end--;"))
            assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt(source,true),source);
        assertTrue(LegacyFogAccess.adapt("bool b=gl_Fog.start==gl_Fog.end||gl_Fog.start<=0.;",true).used());
    }
    @Test void syntheticPostNamesCannotBeSuppliedByPacks() {
        for(String name:List.of("oculusLegacyFogColor","oculusLegacyFogStart","oculusLegacyFogEnd"))
            assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt("float "+name+";",false));
    }
    @Test void sourceReferenceAndExpansionBudgetsFailClosed() {
        assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt(" ".repeat(LegacyFogAccess.MAX_SOURCE+1),true));
        assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt("gl_Fog.start;".repeat(LegacyFogAccess.MAX_REFERENCES+1),true));
        String input=" ".repeat(LegacyFogAccess.MAX_SOURCE-13)+"gl_Fog.color;";
        assertThrows(ShaderCompatibilityException.class,()->LegacyFogAccess.adapt(input,true));
    }
    @Test void nativeTerrainAndShadowInBothMeshesBindFogEvenWithoutAnyLooseUniforms() throws Exception {
        for(boolean shadow:List.of(false,true))for(boolean extended:List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh("void main(){gl_Position=ftransform()+vec4(gl_Fog.start,gl_Fog.end,gl_Fog.scale,gl_Fog.color.a);}",
                "void main(){gl_FragColor=gl_Fog.color+vec4(gl_Fog.start+gl_Fog.end+gl_Fog.scale);}",List.of(0),shadow,extended);
            assertTrue(program.usesWorldMatrices());assertFalse(program.needsShadowMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains(NativeWorldBindings.block()));assertFalse(source.contains("gl_Fog"));
                assertFalse(source.contains("oculusLegacyFog"));
            }
        }
    }
    @Test void typedFogUniformsAndBuiltinMembersShareTheBindingWithoutNameCollisions() throws Exception {
        String declarations="uniform float fogStart,fogEnd;\n";
        var program=NativeTerrainProgram.adaptWithMesh(declarations+"void main(){gl_Position=ftransform()+vec4(fogStart+gl_Fog.start);}",
            declarations+"void main(){gl_FragColor=vec4(fogEnd+gl_Fog.end);}",List.of(0),false,false);
        assertTrue(program.fragmentSource().contains("oculusNative_fogEnd+(oculusNative_fogEnd)"));
        for(String invalid:List.of("uniform int fogStart;","uniform float fogEnd[2];","uniform float fogStart=1.;",
            "uniform float fogEnd,fogEnd;")) {
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(invalid+"\nvoid main(){gl_Position=ftransform();}",
                "void main(){gl_FragColor=vec4(1);}",List.of(0)),invalid);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(invalid+"\nvoid main(){gl_FragColor=vec4(1);}"),invalid);
        }
    }
    @Test void fullscreenVertexAndFragmentBothSupportOnlyTheSameMemberSubset() throws Exception {
        var vertex=new LegacyFinalVertexAdapter().adapt("void main(){gl_Position=ftransform()+vec4(gl_Fog.start+gl_Fog.end+gl_Fog.scale)+gl_Fog.color;}");
        var fragment=new LegacyFinalShaderAdapter().adapt("void main(){gl_FragColor=gl_Fog.color*gl_Fog.scale;}");
        assertEquals(LegacyFogAccess.POST_BINDINGS,vertex.uniforms());assertEquals(LegacyFogAccess.POST_BINDINGS,fragment.uniforms());
        assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalVertexAdapter().adapt("void main(){gl_Position=ftransform()*gl_Fog.density;}"));
        assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt("void main(){gl_FragColor=vec4(gl_Fog.density);}"));
    }
}
