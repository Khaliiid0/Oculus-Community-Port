package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CustomFloatExpressionTest {
    @Test void completeDiagnosticPackPreparesBothNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("custom-time-curves").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("curves", "Curves", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.vertexSource().contains("oculusNative_oculusCustomValue"));
            assertTrue(program.fragmentSource().contains("oculusNative_oculusCustomValue"));
        }
        var bindings=prepared.variants().get("shaders").customUniforms();
        var values=bindings.newState().update(new Object(),AnimationClock.ZERO,6000,0,0,0,0);
        assertEquals(1f,values.boundSlot(bindings.slots().get("declaredBrightness")),1e-6);
        assertEquals(1f,values.boundSlot(bindings.slots().get("declaredFade")));
        assertFalse(bindings.slots().containsKey("declaredPhase")); // Dependency-only: no shader declares it.
    }
    private static final String DEFINITIONS = """
        uniform.float.timeAngle=worldTime / 24000
        variable.float.shadowFadeOut1=clamp((worldTime - 12330) / 230, 0.0, 1.0)
        variable.float.shadowFadeIn1=clamp((worldTime - 13010) / 220, 0.0, 1.0)
        variable.float.shadowFadeOut2=clamp((worldTime - 22770) / 220, 0.0, 1.0)
        variable.float.shadowFadeIn2=clamp((worldTime - 23440) / 230, 0.0, 1.0)
        uniform.float.shadowFade=1.0 - (shadowFadeOut1 - shadowFadeIn1 + shadowFadeOut2 - shadowFadeIn2)
        uniform.float.timeBrightness=max(sin(timeAngle * 6.28318530718), 0.0)
        """;

    @Test void dependentTimeDefinitionsAdaptBothNativeStagesWithoutInventedBindings() throws Exception {
        var custom = NativeCustomUniforms.parse(DEFINITIONS);
        for (boolean shadow : new boolean[] {false, true}) {
            var nativeProgram = NativeTerrainProgram.adaptWithCustomUniforms(
                "uniform float shadowFade; uniform float timeBrightness; void main(){gl_Position=ftransform()*shadowFade+vec4(timeBrightness);}",
                "uniform float shadowFade; uniform float timeBrightness; void main(){gl_FragColor=vec4(shadowFade,timeBrightness,0,1);}",
                List.of(0), shadow, false, custom);
            for (String source : List.of(nativeProgram.vertexSource(), nativeProgram.fragmentSource())) {
                assertTrue(source.contains("oculusNative_worldTime"));
                assertTrue(source.contains("clamp("));
                assertTrue(source.contains("max(sin("));
                assertFalse(source.contains("uniform float shadowFade"));
                assertFalse(source.contains("shadowFadeOut1"));
            }
        }
    }

    @Test void arithmeticPrecedenceFunctionsAndFloatDivisionAreExplicit() {
        assertEquals(7f, value("1+2*3"));
        assertEquals(9f, value("(1+2)*3"));
        assertEquals(.5f, value("1/2"));
        assertEquals(1f, value("clamp(abs(-2),0,1)"));
        assertEquals(.001f, value("1e-3"));
        assertEquals(0f, value("max(sin(0),0)"));
        assertEquals(-2f, value("min(-2,3)"));
    }

    private static float value(String expression) {
        return CustomFloatExpression.parse(expression, name -> null).constant();
    }

    @Test void timeCurvesMatchIndependentReferenceAcrossFullDay() {
        for (int tick = 0; tick < 24000; tick++) {
            final float time = tick;
            var world = new CustomFloatExpression.Value(Float.toString(time), Set.of(), time);
            var expressions = Map.of(
                "out1", "clamp((worldTime-12330)/230,0,1)", "in1", "clamp((worldTime-13010)/220,0,1)",
                "out2", "clamp((worldTime-22770)/220,0,1)", "in2", "clamp((worldTime-23440)/230,0,1)");
            var fade = CustomFloatExpression.parse("1-(out1-in1+out2-in2)",
                name -> CustomFloatExpression.parse(expressions.get(name), ignored -> world));
            float expected = 1 - (clamp((time - 12330) / 230) - clamp((time - 13010) / 220)
                + clamp((time - 22770) / 220) - clamp((time - 23440) / 230));
            assertEquals(expected, fade.constant());
            var brightness = CustomFloatExpression.parse("max(sin((worldTime/24000)*6.28318530718),0)", name -> world);
            assertEquals(Math.max(0f, (float)Math.sin(time / 24000f * (float)(2 * Math.PI))), brightness.constant(), 0.000001f);
        }
    }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    @Test void cyclesUnknownDependenciesAndVariablesAreNotExposed() throws Exception {
        String shader = "uniform float result; float x=result;";
        for (String properties : List.of(
            "uniform.float.result=result+1", "uniform.float.result=other\nvariable.float.other=result",
            "uniform.float.result=unknown+1", "variable.float.result=1",
            "uniform.float.result=x\n#if OPTION\nvariable.float.x=1\n#endif",
            "uniform.float.result=x\nvariable.float.x=1\nvariable.float.x=2",
            "uniform.float.result=x\nvariable.float.x=1\nvariable.float.\\u0078=2"))
            assertEquals(shader, NativeCustomUniforms.parse(properties).adapt(shader), properties);
        assertTrue(NativeCustomUniforms.parse("uniform.float.result=x+1\nvariable.float.x=2")
            .adapt(shader).contains("(2.0 + 1.0)"));
    }

    @Test void injectionInvalidMathAndComplexityStayBounded() {
        for (String source : List.of("1;discard", "1/0", "1/worldTime", "1e999", "clamp(1,2,0)",
            "sin()", "sin(1,2)", "random()", "vec2(1,2)", "worldTime.x", "1 /*comment*/ +2",
            "(".repeat(80)+"1"+")".repeat(80), "1+".repeat(200)+"1", "1".repeat(2049)))
            assertThrows(RuntimeException.class, () -> CustomFloatExpression.parse(source,
                name -> new CustomFloatExpression.Value("float(worldTime)", Set.of("worldTime"), null)), source);
    }

    @Test void forwardReferencesFingerprintAndExponentialExpansionAreBounded() throws Exception {
        String shader = "uniform float result; float x=result;";
        String first = NativeCustomUniforms.parse("uniform.float.result=helper*2\nvariable.float.helper=worldTime/24000").adapt(shader);
        String second = NativeCustomUniforms.parse("uniform.float.result=helper*3\nvariable.float.helper=worldTime/24000").adapt(shader);
        assertNotEquals(first, second);
        StringBuilder properties = new StringBuilder("uniform.float.result=v20\nvariable.float.v0=worldTime\n");
        for (int i=1;i<=20;i++) properties.append("variable.float.v").append(i).append("=v").append(i-1).append("+v").append(i-1).append('\n');
        assertEquals(shader, NativeCustomUniforms.parse(properties.toString()).adapt(shader));
    }

    @Test void repeatedShaderUsesCannotAmplifyExpressionsBeyondSourceBudget() {
        StringBuilder properties = new StringBuilder("uniform.float.result=v8\nvariable.float.v0=worldTime\n");
        for (int i=1;i<=8;i++) properties.append("variable.float.v").append(i).append("=v").append(i-1).append("+v").append(i-1).append('\n');
        var custom = NativeCustomUniforms.parse(properties.toString());
        assertThrows(ShaderCompatibilityException.class,
            () -> custom.adapt("uniform float result;\nfloat x=" + "result+".repeat(2000) + "0;"));
    }
}
