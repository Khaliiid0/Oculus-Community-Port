package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.WorldMatrixData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class AnimationClockTest {
    @Test void probePreparesActualNativeAndPostAnimationBindings() throws Exception {
        var resource = getClass().getClassLoader().getResource("animation-terrain");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("animation", "Animation probe",
                java.nio.file.Path.of(resource.toURI()), dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            assertTrue(program.vertexSource().contains("float(oculusNative_frameCounter)"));
            assertTrue(program.fragmentSource().contains("oculusNative_frameTimeCounter"));
        }
        var semantics = prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.containsAll(List.of(LegacyFinalShaderAdapter.UniformSemantic.FRAME_COUNTER,
            LegacyFinalShaderAdapter.UniformSemantic.FRAME_TIME, LegacyFinalShaderAdapter.UniformSemantic.FRAME_TIME_COUNTER)));
    }

    @Test void consumersShareOneStableSnapshotAndReadDoesNotAdvanceIt() {
        var clock = new AnimationClock();
        assertEquals(AnimationClock.ZERO, clock.current());
        var first = clock.beginFrame(0);
        for (int i = 0; i < 40; i++) assertSame(first, clock.current());
        clock.endFrame(10_000_000);
        assertSame(first, clock.current());
        assertEquals(1, clock.beginFrame(20_000_000).frameCounter());
        assertEquals(0, first.frameCounter());
    }

    @Test void durationComesFromPreviousCompletedFrameAndCounterUsesWallTime() {
        var clock = new AnimationClock();
        clock.beginFrame(0); clock.endFrame(10_000_000);
        var second = clock.beginFrame(20_000_000);
        assertEquals(0, second.frameTime());
        clock.endFrame(35_000_000);
        var third = clock.beginFrame(50_000_000);
        assertEquals(.025F, third.frameTime(), .000001F);
        assertEquals(.05F, third.frameTimeCounter(), .000001F);
        // A stall/pause is elapsed wall time, not a clamped one-second animation step.
        assertEquals(12.05F, clock.beginFrame(12_050_000_000L).frameTimeCounter(), .00001F);
    }

    @Test void hourWrapAndFloatRoundingStayInsideRange() {
        var clock = new AnimationClock();
        clock.beginFrame(0);
        assertTrue(clock.beginFrame(3_599_999_999_999L).frameTimeCounter() < 3600);
        assertEquals(.25F, clock.beginFrame(3_600_250_000_000L).frameTimeCounter(), .000001F);
        assertEquals(.5F, clock.beginFrame(10_800_500_000_000L).frameTimeCounter(), .000001F);
    }

    @Test void frameCounterWrapsWithoutDependingOnConsumerCount() {
        var clock = new AnimationClock();
        for (int i = 0; i <= 720720; i++) assertEquals(i % 720720, clock.beginFrame(i).frameCounter());
    }

    @Test void signedNanoTimeRolloverAndBackwardSamplesRemainFinite() {
        var clock = new AnimationClock();
        clock.beginFrame(Long.MAX_VALUE - 9);
        assertEquals(20E-9F, clock.beginFrame(Long.MIN_VALUE + 10).frameTimeCounter(), 1E-12F);
        var other = new AnimationClock();
        other.beginFrame(100); other.endFrame(100);
        other.endFrame(50);
        assertEquals(AnimationClock.ZERO.frameTime(), other.beginFrame(50).frameTime());
        assertEquals(0, other.current().frameTimeCounter());
    }

    @Test void nativePayloadPreservesClockOffsetsWithinExpandedBlock() {
        var matrix = new Matrix4f();
        var frame = new AnimationClock.Snapshot(720719, .016F, 1234.5F);
        var data = new WorldMatrixData(matrix, matrix, matrix, matrix, 0, 0, 0, 854, 480, 24000, frame);
        for (ByteBuffer bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),
            ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.position(8).limit(NativeWorldBindings.BYTE_SIZE + 8);
            data.write(bytes);
            var read = bytes.duplicate().order(ByteOrder.nativeOrder());
            assertEquals(1, read.getInt(8 + 544));
            assertEquals(frame.frameCounter(), read.getInt(8 + 548));
            assertEquals(frame.frameTime(), read.getFloat(8 + 552));
            assertEquals(frame.frameTimeCounter(), read.getFloat(8 + 556));
            assertEquals(8, bytes.position());
            assertEquals(0, bytes.getInt(0));
            assertEquals(0, bytes.duplicate().clear().getInt(NativeWorldBindings.BYTE_SIZE + 8));
        }
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
    }

    @Test void nativeAnimationBindsInEveryStageAndMeshMode() throws Exception {
        String declarations = "uniform int frameCounter;\nuniform float frameTime, frameTimeCounter;\n";
        String value = "vec4(float(frameCounter) + frameTime + frameTimeCounter)";
        for (boolean shadow : new boolean[] {false, true}) for (boolean extended : new boolean[] {false, true}) {
            var result = NativeTerrainProgram.adaptWithMesh(declarations +
                "void main() { gl_Position = ftransform() + " + value + "; }",
                declarations + "void main() { gl_FragColor = " + value + "; }", List.of(0), shadow, extended);
            assertTrue(result.usesWorldMatrices());
            for (String source : List.of(result.vertexSource(), result.fragmentSource()))
                assertTrue(source.contains("float(oculusNative_frameCounter) + oculusNative_frameTime + oculusNative_frameTimeCounter"));
        }
    }

    @Test void incorrectAnimationDeclarationsStillReject() {
        for (String declaration : List.of("uniform float frameCounter;", "uniform int frameTime;",
            "uniform float frameTimeCounter[2];", "uniform float frameTime, frameTime;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                declaration + "void main() { gl_Position = ftransform(); }",
                "void main() { gl_FragColor = vec4(1); }", List.of(0)));
    }
}
