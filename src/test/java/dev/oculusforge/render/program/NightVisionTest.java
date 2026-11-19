package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class NightVisionTest {
    @Test void diagnosticFixtureRequiresNativeTerrainShadowAndPostValue() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("night-vision").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("night-vision", "Night vision", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertTrue(world.nativeTerrain().usesWorldMatrices());
        assertTrue(world.nativeShadow().usesWorldMatrices());
        assertTrue(prepared.programs().getFirst().fragment().uniforms().stream()
            .anyMatch(u -> u.semantic() == LegacyFinalShaderAdapter.UniformSemantic.NIGHT_VISION));
    }

    @Test void snapshotPreservesTransitionsAndRejectsInvalidValues() {
        assertEquals(0f, WorldEnvironment.CLEAR.nightVision());
        for (float value : new float[] {0, .4f, .7f, 1})
            assertEquals(value, new WorldEnvironment(0, 0, value).nightVision());
        for (float value : new float[] {-.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new WorldEnvironment(0, 0, value));
    }

    @Test void nativeTerrainAndShadowAdaptBothStages() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(
                "uniform float nightVision;\nvoid main(){gl_Position=ftransform()+vec4(nightVision);}",
                "uniform float nightVision;\nvoid main(){gl_FragColor=vec4(nightVision);}",
                List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource()))
                assertTrue(source.contains("vec4(oculusNative_nightVision)"));
        }
    }

    @Test void malformedNativeDeclarationsFail() {
        for (String declaration : List.of("uniform int nightVision;", "uniform float nightVision[2];",
            "uniform float nightVision,nightVision;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                declaration + "\nvoid main(){gl_Position=ftransform();}",
                "void main(){gl_FragColor=vec4(1);}", List.of(0)));
    }

    @Test void nativeWriterPreservesOffsetsAndInitializesPaddingAndDefaultEyeLight() {
        var matrix = new Matrix4f();
        for (var bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16), ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());
            for (int i = 0; i < bytes.capacity(); i++) bytes.put(i, (byte) 0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            new WorldMatrixData(matrix, matrix, matrix, matrix, 1, 2, 3, 854, 480, 0,
                AnimationClock.ZERO, RelativeEyePosition.ZERO, new HeldLight(14, 7),
                new WorldEnvironment(5, .75f, .4f)).write(bytes);
            assertEquals(14, bytes.getInt(8 + 576));
            assertEquals(7, bytes.getInt(8 + 580));
            assertEquals(5, bytes.getInt(8 + 584));
            assertEquals(.75f, bytes.getFloat(8 + 588));
            assertEquals(.4f, bytes.getFloat(8 + 592));
            assertEquals(0, bytes.getInt(8 + 596)); // alignment before eyeBrightness
            assertEquals(0, bytes.getInt(8 + 600)); // default block light
            assertEquals(0, bytes.getInt(8 + 604)); // default sky light
            assertEquals(0x5a5a5a5a, bytes.getInt(0));
            assertEquals(0x5a5a5a5a, bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            assertEquals(8, bytes.position());
        }
    }

    @Test void postWriterReadsSnapshotWithoutLivePlayerAccess() throws Exception {
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class, LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class, int.class, WorldEnvironment.class, WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        try (var program = new PackFinalProgram("test", "final", null, List.of(), List.of(), null)) {
            for (float value : new float[] {1, .7f, .4f, 0}) {
                var bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                writer.invoke(program, com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
                    LegacyFinalShaderAdapter.UniformSemantic.NIGHT_VISION, null, 854, 480,
                    new WorldEnvironment(0, 0, value), null, null);
                assertEquals(value, bytes.getFloat(0));
            }
        }
    }
}
