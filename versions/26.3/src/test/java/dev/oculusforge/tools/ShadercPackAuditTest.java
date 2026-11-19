package dev.oculusforge.tools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

class ShadercPackAuditTest {
    @Test void everyPackAlphaFunctionAndInheritedFallbackCompileInRealFragmentStages() throws Exception {
        var program=dev.oculusforge.render.program.NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",
            "void main(){gl_FragColor=vec4(1,1,1,.02);}",java.util.List.of(0));
        var pair=dev.oculusforge.port.ShadercInterfacePair.adapt(program.vertexSource(),program.fragmentSource());
        try(var compiler=new ShadercPackAuditMain.Compiler()) {
            for(boolean zeroToOne:new boolean[]{false,true}) {
                for(var function:dev.oculusforge.render.program.TerrainAlphaSettings.Function.values()) {
                    var test=new dev.oculusforge.render.program.TerrainAlphaSettings.Test(function,.001f);
                    var result=compiler.compile(pair.fragment(),Shaderc.shaderc_fragment_shader,zeroToOne,test.defines());
                    assertTrue(result.success(),result.message());
                }
                assertTrue(compiler.compile(pair.fragment(),Shaderc.shaderc_fragment_shader,zeroToOne,java.util.Map.of("ALPHA_CUTOUT","0.1")).success());
                assertFalse(compiler.compile(pair.fragment(),Shaderc.shaderc_fragment_shader,zeroToOne,
                    java.util.Map.of("OCULUS_NATIVE_ALPHA_MODE","99","OCULUS_NATIVE_ALPHA_REF","0.1")).success());
            }
        }
    }
    @Test void actualCompilerAcceptsValidInputsRejectsSyntaxErrorsAndRecovers() {
        try (var compiler = new ShadercPackAuditMain.Compiler()) {
            for (boolean zeroToOne : new boolean[] {false, true}) {
                var valid = compiler.compile("#version 450\nvoid main(){gl_Position=vec4(0,0,0,1);}", Shaderc.shaderc_vertex_shader, zeroToOne);
                assertTrue(valid.success(), valid.message());
                var invalid = compiler.compile("#version 450\nlayout(location=0) out vec4 c;void main(){c=missingSymbol;}", Shaderc.shaderc_fragment_shader, zeroToOne);
                assertFalse(invalid.success());
                assertTrue(invalid.message().contains("missingSymbol"));
                var recovered = compiler.compile("#version 450\nlayout(location=0) out vec4 c;void main(){c=vec4(1);}", Shaderc.shaderc_fragment_shader, zeroToOne);
                assertTrue(recovered.success(), recovered.message());
                var large = compiler.compile("#version 450\n/*" + "padding ".repeat(20_000)
                    + "*/\nvoid main(){gl_Position=vec4(1);}", Shaderc.shaderc_vertex_shader, zeroToOne);
                assertTrue(large.success(), large.message());
            }
        }
    }

    @Test void nativeCompilerLifetimeAndInputContractsFailBeforeUnsafeCalls() {
        var compiler = new ShadercPackAuditMain.Compiler();
        try {
            assertThrows(NullPointerException.class, () -> compiler.compile(null, Shaderc.shaderc_vertex_shader, false));
            assertThrows(IllegalArgumentException.class, () -> compiler.compile("", Shaderc.shaderc_geometry_shader, false));
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(" ".repeat(32 * 1024 * 1024 + 1), Shaderc.shaderc_vertex_shader, false));
        } finally { compiler.close(); }
        compiler.close();
        assertThrows(IllegalStateException.class, () -> compiler.compile("", Shaderc.shaderc_vertex_shader, false));
    }
}
