package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CloudHeightDefaultTest {
    @Test void literalFallbackNeverTurnsNativeOrPostCloudHeightIntoAConstant() throws Exception {
        for(String fallback:List.of("192.0","-64","+.5","2.0e2","192.0F")) {
            String source="uniform float cloudHeight = "+fallback+";\nvoid main(){gl_FragColor=vec4(cloudHeight);}";
            var nativeProgram=NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",source,List.of(0));
            assertTrue(nativeProgram.fragmentSource().contains("oculusNative_previousCameraPosition.w"));
            var post=new LegacyFinalShaderAdapter().adapt(source);
            assertEquals(List.of(new LegacyFinalShaderAdapter.UniformBinding("cloudHeight",
                LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT)),post.uniforms());
            assertFalse(post.source().contains("const float cloudHeight"));
        }
    }
    @Test void normalizationPreservesSourceOffsetsAndNewlines() throws Exception {
        String source="uniform float cloudHeight =\n192.0;\nfloat value=cloudHeight;";
        String result=NativeWorldBindings.normalizeCloudHeightDefault(source);
        assertEquals(source.length(),result.length());assertEquals(source.lines().count(),result.lines().count());
        assertTrue(result.contains("float value=cloudHeight;"));assertFalse(result.contains("192.0"));
    }
    @Test void invalidTypesArraysDuplicatesNonfiniteAndComplexFallbacksStillReject() {
        for(String declaration:List.of("uniform int cloudHeight=192;","uniform float cloudHeight[2];",
            "uniform float cloudHeight=1e999;","uniform float cloudHeight=NaN;","uniform float cloudHeight=192.0u;",
            "uniform float cloudHeight=1.+2.;","uniform float cloudHeight=func();",
            "uniform float cloudHeight;\nuniform float cloudHeight=192.;")) {
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",
                declaration+"\nvoid main(){gl_FragColor=vec4(cloudHeight);}",List.of(0)),declaration);
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(declaration
                +"\nvoid main(){gl_FragColor=vec4(cloudHeight);}"),declaration);
        }
    }
}
