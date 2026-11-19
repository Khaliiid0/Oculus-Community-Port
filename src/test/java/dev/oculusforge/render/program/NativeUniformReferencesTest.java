package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class NativeUniformReferencesTest {
    @Test void localFogColorIsNotRewrittenAsUniformSwizzle() throws Exception {
        String source="uniform vec3 fogColor;\nvec3 a(){return fogColor;}\nvec3 b(){vec3 fogColor=vec3(1);return fogColor;}\nvec3 c(){return fogColor;}";
        String result=NativeWorldBindings.adapt(source).source();
        assertTrue(result.contains("vec3 b(){vec3 fogColor=vec3(1);return fogColor;}"));
        assertEquals(2,result.split("oculusNative_fogColor.xyz",-1).length-1);
        assertFalse(result.contains("vec3 oculusNative_fogColor.xyz"));
    }
    @Test void nestedScopesRestoreGlobalBindingAndRespectDeclarationOrder() throws Exception {
        String source="uniform float far; void f(){float a=far;{float far=2;{float b=far;}float c=far;}float d=far;}";
        String result=NativeWorldBindings.adapt(source).source();
        assertTrue(result.contains("float a=oculusNative_far;"));
        assertTrue(result.contains("float far=2;{float b=far;}float c=far;"));
        assertTrue(result.contains("float d=oculusNative_far;"));
    }
    @Test void memberFieldsAreNotUniformReferences() throws Exception {
        String result=NativeWorldBindings.adapt("uniform vec3 fogColor; vec3 f(){return light.fogColor+fogColor;}").source();
        assertTrue(result.contains("light.fogColor+oculusNative_fogColor.xyz"));
    }
    @Test void constructorAndFunctionCommasAreNotDeclarationLists() throws Exception {
        String result=NativeWorldBindings.adapt("uniform vec3 fogColor; void f(){vec3 x=mix(vec3(0),fogColor,0.5);}").source();
        assertTrue(result.contains("mix(vec3(0),oculusNative_fogColor.xyz,0.5)"));
    }
    @Test void unsupportedShadowingFormsFailWithExplicitDiagnostic() {
        for(String body:new String[]{"vec3 f(vec3 fogColor){return fogColor;}",
            "void f(){for(vec3 fogColor=vec3(0);true;){}}", "void f(){vec3 x, fogColor;}",
            "void f(){vec3 x=vec3(0), fogColor=vec3(1);}"})
            assertThrows(ShaderCompatibilityException.class,()->NativeWorldBindings.adapt("uniform vec3 fogColor;\n"+body));
    }
}
