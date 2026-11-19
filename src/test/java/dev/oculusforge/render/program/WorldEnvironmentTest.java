package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class WorldEnvironmentTest {
    @Test void postOnlyEnvironmentFixtureHasNoNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("post-environment").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("post-environment", "Post environment", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(1, prepared.programs().size());
        for (var variant : prepared.world().variants().values()) {
            assertNull(variant.nativeTerrain());
            assertNull(variant.nativeShadow());
        }
        var semantics = prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.containsAll(List.of(LegacyFinalShaderAdapter.UniformSemantic.MOON_PHASE,
            LegacyFinalShaderAdapter.UniformSemantic.RAIN_STRENGTH,
            LegacyFinalShaderAdapter.UniformSemantic.RAIN_FACTOR)));
    }

    private static final class CameraEnvironment implements WorldEnvironmentState {
        private WorldEnvironment value;
        public WorldEnvironment oculus$getEnvironment() { return value; }
        public void oculus$setEnvironment(WorldEnvironment value) { this.value = value; }
    }

    @Test void missingOrInvalidatedFrameCannotSupplyStalePostValues() {
        var state = new CameraEnvironment();
        assertThrows(IllegalStateException.class, state::oculus$requireEnvironment);
        var oldFrame = new WorldEnvironment(7, 1);
        state.oculus$setEnvironment(oldFrame);
        assertSame(oldFrame, state.oculus$requireEnvironment());
        state.oculus$setEnvironment(null);
        assertThrows(IllegalStateException.class, state::oculus$requireEnvironment);
        var newFrame = new WorldEnvironment(0, 0);
        state.oculus$setEnvironment(newFrame);
        assertSame(newFrame, state.oculus$requireEnvironment());
        assertEquals(7, oldFrame.moonPhase());
        assertEquals(1, oldFrame.rainStrength());
    }

    @Test void cameraSnapshotsDoNotShareEnvironmentAcrossWorlds() {
        var overworld = new CameraEnvironment();
        var nether = new CameraEnvironment();
        overworld.oculus$setEnvironment(new WorldEnvironment(5, .75f));
        assertThrows(IllegalStateException.class, nether::oculus$requireEnvironment);
        nether.oculus$setEnvironment(WorldEnvironment.CLEAR);
        assertEquals(5, overworld.oculus$requireEnvironment().moonPhase());
        assertEquals(0, nether.oculus$requireEnvironment().moonPhase());
    }

    @Test void postWriterUsesSnapshotWithoutSkyOrWeatherState() throws Exception {
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class,
            int.class, int.class, WorldEnvironment.class,
            WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        try (var program = new PackFinalProgram("test", "final", null, List.of(), List.of(), null)) {
            for (var environment : List.of(new WorldEnvironment(7, .75f), WorldEnvironment.CLEAR)) {
                var bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                var builder = com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
                // A null LevelRenderState proves these bindings cannot reach stale sky/weather fields.
                for (var semantic : List.of(LegacyFinalShaderAdapter.UniformSemantic.MOON_PHASE,
                    LegacyFinalShaderAdapter.UniformSemantic.RAIN_STRENGTH,
                    LegacyFinalShaderAdapter.UniformSemantic.RAIN_FACTOR)) {
                    writer.invoke(program, builder, semantic, null, 854, 480, environment, null, null);
                }
                assertEquals(environment.moonPhase(), bytes.getInt(0));
                assertEquals(environment.rainStrength(), bytes.getFloat(4));
                assertEquals(environment.rainStrength(), bytes.getFloat(8));
            }
        }
    }

    @Test void environmentGpuFixturePreparesBothNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("world-environment").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("world-environment", "Environment probe", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertEquals(1, prepared.programs().size());
        var postSemantics = prepared.programs().getFirst().fragment().uniforms().stream()
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(postSemantics.contains(LegacyFinalShaderAdapter.UniformSemantic.MOON_PHASE));
        assertTrue(postSemantics.contains(LegacyFinalShaderAdapter.UniformSemantic.RAIN_STRENGTH));
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        for (var program : List.of(world.nativeTerrain(), world.nativeShadow())) {
            assertTrue(program.usesWorldMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_moonPhase"));
                assertTrue(source.contains("oculusNative_rainStrength"));
            }
        }
    }

    @Test void validPhasesAndRainTransitionsArePreserved() {
        for (int phase = 0; phase < 8; phase++) {
            for (float rain : new float[] {0, .125f, .5f, 1}) {
                var value = new WorldEnvironment(phase, rain);
                assertEquals(phase, value.moonPhase());
                assertEquals(rain, value.rainStrength());
            }
        }
    }

    @Test void invalidSnapshotIsRejectedRatherThanClamped() {
        for (int phase : new int[] {-1, 8, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new WorldEnvironment(phase, 0));
        for (float rain : new float[] {-.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new WorldEnvironment(0, rain));
    }

    @Test void bothNativeStagesAndMeshModesBindWorldInputs() throws Exception {
        String decl = "uniform int moonPhase;\nuniform float rainStrength;\n";
        for (boolean shadow : new boolean[] {false, true}) {
            for (boolean extended : new boolean[] {false, true}) {
                var result = NativeTerrainProgram.adaptWithMesh(
                    decl + "void main(){gl_Position=ftransform()+vec4(float(moonPhase)+rainStrength);}",
                    decl + "void main(){gl_FragColor=vec4(float(moonPhase)+rainStrength);}",
                    List.of(0), shadow, extended);
                assertTrue(result.usesWorldMatrices());
                assertFalse(result.needsShadowMatrices());
                for (String source : List.of(result.vertexSource(), result.fragmentSource())) {
                    assertTrue(source.contains("float(oculusNative_moonPhase)+oculusNative_rainStrength"));
                    assertTrue(source.contains(NativeWorldBindings.block()));
                }
            }
        }
    }

    @Test void wrongTypesArraysAndDuplicatesFailExplicitly() {
        for (String decl : List.of("uniform float moonPhase;", "uniform int rainStrength;",
            "uniform int moonPhase[2];", "uniform int moonPhase,moonPhase;",
            "uniform float rainStrength[2];", "uniform float rainStrength,rainStrength;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                decl + "\nvoid main(){gl_Position=ftransform();}", "void main(){gl_FragColor=vec4(1);}", List.of(0)));
    }

    @Test void environmentOffsetsRemainStableWhenBlockGrows() {
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var identity = new Matrix4f();
        for (ByteBuffer bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16), ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder()).position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            for (var environment : List.of(new WorldEnvironment(7, .75f), WorldEnvironment.CLEAR)) {
                new WorldMatrixData(identity, identity, identity, identity, 1, 2, 3, 854, 480, 24007,
                    new AnimationClock.Snapshot(42, .01f, 2), new RelativeEyePosition(1, -2, 3),
                    new HeldLight(14, 7), environment).write(bytes);
                assertEquals(42, bytes.getInt(8 + 548));
                assertEquals(-2f, bytes.getFloat(8 + 564));
                assertEquals(14, bytes.getInt(8 + 576));
                assertEquals(7, bytes.getInt(8 + 580));
                assertEquals(environment.moonPhase(), bytes.getInt(8 + 584));
                assertEquals(environment.rainStrength(), bytes.getFloat(8 + 588));
                assertEquals(8, bytes.position());
                assertEquals(0, bytes.getInt(0));
            assertEquals(0, bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            }
        }
    }
}
