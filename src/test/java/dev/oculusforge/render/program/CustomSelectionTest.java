package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CustomSelectionTest {
    @Test void heightDiagnosticPreparesTerrainAndShadow() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("custom-height-selection").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("height", "Height", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        for(var program:List.of(world.nativeTerrain(),world.nativeShadow())) {
            assertTrue(program.vertexSource().contains("oculusNative_oculusCustomValue0"));
            assertTrue(program.fragmentSource().contains("oculusNative_oculusCustomValue0"));
        }
        var bindings=prepared.variants().get("shaders").customUniforms();
        var state=bindings.newState();var level=new Object();
        for(float height:new float[]{112,113,153,193}) {
            float expected=height<113?0:height<153?1f/3:height<193?2f/3:1;
            assertEquals(expected,state.update(level,new AnimationClock.Snapshot(0,0,0),0,0,height,0,0).boundSlot(0));
        }
    }
    private static float evaluate(String source, float height) {
        return CustomFloatExpression.parse(source, name -> name.equals("cameraPosition.y")
            ? new CustomFloatExpression.Value(Float.toString(height), Set.of(), height) : null).constant();
    }

    @Test void heightThresholdsMatchBelowAtAndAboveBoundary() {
        for (int threshold : new int[] {113,153,193}) {
            String expression = "if(cameraPosition.y >= " + threshold + ",1,0)";
            assertEquals(0f, evaluate(expression, Math.nextDown((float) threshold)));
            assertEquals(1f, evaluate(expression, threshold));
            assertEquals(1f, evaluate(expression, Math.nextUp((float) threshold)));
        }
    }

    @Test void orderedComparisonsPrecedenceAndNestedSelections() {
        assertEquals(2f, evaluate("if(2+3*4 > 13,2,7)",0));
        assertEquals(7f, evaluate("if(14 > 14,2,7)",0));
        assertEquals(2f, evaluate("if(-1 < 0,2,7)",0));
        assertEquals(2f, evaluate("if(-1 <= -1,2,7)",0));
        assertEquals(7f, evaluate("if(0 < -1,2,7)",0));
        assertEquals(7f, evaluate("if(1 <= 0,2,7)",0));
        assertEquals(5f, evaluate("1+if(cameraPosition.y>=113,if(cameraPosition.y<153,4,8),2)",120));
        assertEquals(.5f, evaluate("1/if(1<2,2,4)",0));
    }

    @Test void branchesAndComparisonDependenciesBindBothNativeStages() throws Exception {
        var custom = NativeCustomUniforms.parse("""
            variable.float.heightGate=if(cameraPosition.y >= 113.0, 1, 0)
            uniform.float.probe=if(heightGate>0,eyeAltitude,worldTime/24000)
            """);
        for (boolean shadow : new boolean[] {false,true}) {
            var program = NativeTerrainProgram.adaptWithCustomUniforms(
                "uniform float probe; void main(){gl_Position=ftransform()+vec4(probe);}",
                "uniform float probe; void main(){gl_FragColor=vec4(probe);}", List.of(0),shadow,false,custom);
            for (String source : List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_cameraPosition.xyz.y >= 113.0"));
                assertTrue(source.contains("oculusNative_cameraPosition.y"));
                assertTrue(source.contains("float(oculusNative_worldTime)"));
                assertTrue(source.contains(" ? "));
                assertFalse(source.contains("heightGate"));
            }
        }
    }

    @Test void invalidOrUnsupportedConditionsAndDeadBranchesFailClosed() {
        for (String expression : List.of("if(1,2,3)","if(true,2,3)","if(1==1,2,3)",
            "if(1<2<3,4,5)","if(1<2&&2<3,4,5)","if(1<2,3)","if(1<2,3,4,5)",
            "if(1<2,3,1/0)","if(1<2,3,unknown)","if(1<2,3,1e999)",
            "if(1<2,3,random())","if(1 > = 0,2,3)","if(1<2,3,4);discard",
            "if(1<2,1,".repeat(30)+"0"+")".repeat(30)))
            assertThrows(RuntimeException.class,()->evaluate(expression,0),expression);
    }

    @Test void rejectedConditionalDefinitionDoesNotSuppressIndependentUniform() throws Exception {
        var custom=NativeCustomUniforms.parse("uniform.float.bad=if(1<2,3,unknown)\nuniform.float.good=if(1>=1,4,5)");
        String adapted=custom.adapt("uniform float bad; uniform float good; float x=bad+good;");
        assertTrue(adapted.contains("uniform float bad;"));
        assertFalse(adapted.contains("uniform float good;"));
        assertTrue(adapted.contains(" ? 4.0 : 5.0"));
    }
}
