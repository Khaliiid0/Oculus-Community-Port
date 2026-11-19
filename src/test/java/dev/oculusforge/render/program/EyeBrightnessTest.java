package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class EyeBrightnessTest {
    @Test void diagnosticFixturePreparesNativeAndPostBindings() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("eye-brightness").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("eye-brightness", "Eye brightness", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            assertTrue(program.vertexSource().contains("oculusNative_eyeBrightness"));
            assertTrue(program.fragmentSource().contains("oculusNative_eyeBrightness"));
        }
        assertTrue(prepared.programs().getFirst().fragment().uniforms().stream()
            .anyMatch(u -> u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS));
    }

    @Test void allRawLightPairsKeepLayerOrderAndScaleExactly() {
        for (int block = 0; block <= 15; block++) for (int sky = 0; sky <= 15; sky++) {
            var value = new EyeBrightness(block, sky);
            assertEquals(block * 16, value.blockLightmap());
            assertEquals(sky * 16, value.skyLightmap());
            assertEquals(Math.max(block, sky) / 15f, value.maximum());
        }
    }

    @Test void invalidLightCannotBecomeAFrameSnapshot() {
        for (int value : new int[] {-1, 16, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new EyeBrightness(value, 0));
            assertThrows(IllegalArgumentException.class, () -> new EyeBrightness(0, value));
        }
        assertThrows(NullPointerException.class, () -> new WorldEnvironment(0, 0, 0, null));
    }

    @Test void rawBindingSupportsBothNativeStagesAndRejectsMalformedDeclarations() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(
                "uniform ivec2 eyeBrightness;\nvoid main(){gl_Position=ftransform()+vec4(vec2(eyeBrightness),0,0);}",
                "uniform ivec2 eyeBrightness;\nvoid main(){gl_FragColor=vec4(vec2(eyeBrightness),0,1);}",
                List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource()))
                assertTrue(source.contains("vec2(oculusNative_eyeBrightness)"));
        }
        for (String declaration : List.of("uniform vec2 eyeBrightness;", "uniform ivec2 eyeBrightness[2];",
            "uniform ivec2 eyeBrightness,eyeBrightness;", "uniform vec2 eyeBrightnessSmooth;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                declaration + "\nvoid main(){gl_Position=ftransform();}",
                "void main(){gl_FragColor=vec4(1);}", List.of(0)));
    }

    @Test void postRawAndSmoothHaveDistinctSemantics() throws Exception {
        var shader = new LegacyFinalShaderAdapter().adapt("uniform ivec2 eyeBrightness;\n"
            + "uniform ivec2 eyeBrightnessSmooth;\nvoid main(){gl_FragColor=vec4(vec2(eyeBrightness+eyeBrightnessSmooth),0,1);}");
        assertTrue(shader.uniforms().stream().anyMatch(u -> u.name().equals("eyeBrightness")
            && u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS));
        assertTrue(shader.uniforms().stream().anyMatch(u -> u.name().equals("eyeBrightnessSmooth")
            && u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH));
    }

    @Test void nativeIvec2UsesEightByteAlignmentAndPreservesRawOffsets() {
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var identity = new Matrix4f();
        for (var bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16), ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());
            for (int i = 0; i < bytes.capacity(); i++) bytes.put(i, (byte) 0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(identity, identity, identity, identity, 0, 0, 0, 854, 480, 0,
                AnimationClock.ZERO, RelativeEyePosition.ZERO, new HeldLight(14, 7),
                new WorldEnvironment(5, .75f, .4f, new EyeBrightness(3, 14)),
                new EyeBrightnessSmoother.Value(120, 80)).write(bytes);
            assertEquals(14, bytes.getInt(8 + 576));
            assertEquals(5, bytes.getInt(8 + 584));
            assertEquals(.75f, bytes.getFloat(8 + 588));
            assertEquals(.4f, bytes.getFloat(8 + 592));
            assertEquals(0, bytes.getInt(8 + 596));
            assertEquals(48, bytes.getInt(8 + 600));
            assertEquals(224, bytes.getInt(8 + 604));
            assertEquals(120, bytes.getInt(8 + 608));
            assertEquals(80, bytes.getInt(8 + 612));
            assertEquals(0L, bytes.getLong(8 + 616));
            assertEquals(0x5a5a5a5a, bytes.getInt(0));
            assertEquals(0x5a5a5a5a, bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            assertEquals(8, bytes.position());
        }
    }

    @Test void postUsesSnapshotWithNoLiveWorldAccessAndClearsBetweenFrames() throws Exception {
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class, LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class, int.class, WorldEnvironment.class, WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        var smoothField = PackFinalProgram.class.getDeclaredField("smoothedEyeBrightness");
        smoothField.setAccessible(true);
        try (var program = new PackFinalProgram("test", "final", null, List.of(), List.of(), null)) {
            for (var light : List.of(new EyeBrightness(3, 14), new EyeBrightness(15, 0), EyeBrightness.DARK)) {
                var bytes = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
                var builder = com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
                smoothField.set(program, new EyeBrightnessSmoother.Value(120, 80));
                for (var semantic : List.of(LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS,
                    LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH,
                    LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_MAX))
                    writer.invoke(program, builder, semantic, null, 854, 480,
                        new WorldEnvironment(0, 0, 0, light), null, null);
                assertEquals(light.blockLightmap(), bytes.getInt(0));
                assertEquals(light.skyLightmap(), bytes.getInt(4));
                assertEquals(120, bytes.getInt(8));
                assertEquals(80, bytes.getInt(12));
                assertEquals(light.maximum(), bytes.getFloat(16));
            }
        }
    }
}
