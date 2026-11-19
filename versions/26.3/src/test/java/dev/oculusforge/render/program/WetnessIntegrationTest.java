package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import dev.oculusforge.shaderpack.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WetnessIntegrationTest {
    @TempDir Path directory;
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "void main(){gl_FragColor=vec4(1);}";

    private void shader(String name, String source) throws Exception {
        var path = directory.resolve("shaders/" + name);
        Files.createDirectories(path.getParent()); Files.writeString(path, source);
    }
    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("wet-test", "Wet test", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void collectorDefaultsCommentsAndIndependentMatchingLiterals() throws Exception {
        var collector = new WetnessSettings.Collector();
        collector.read("// const float wetnessHalflife=9.;\n/* const float drynessHalflife=2.; */");
        assertEquals(WetnessSettings.DEFAULT, collector.settings());
        collector.read("const float wetnessHalflife=1e1;const float wetnessHalflife=10.0f;");
        assertEquals(new WetnessSettings(10, 200), collector.settings());
        collector.read("const float drynessHalflife=+2.5e1F;");
        assertEquals(new WetnessSettings(10, 25), collector.settings());
        assertThrows(ShaderCompatibilityException.class,
            () -> collector.read("const float drynessHalflife=26.;"));
    }

    @Test void invalidDirectiveTypesValuesExpressionsAndCrossStageConflictsReject() throws Exception {
        for (String name : List.of("wetnessHalflife", "drynessHalflife")) {
            for (String literal : List.of("-1.0", "1e999", "NaN", "1.0*2.0", "unknown"))
                assertThrows(ShaderCompatibilityException.class,
                    () -> new WetnessSettings.Collector().read("const float "+name+"="+literal+";"));
            assertThrows(ShaderCompatibilityException.class,
                () -> new WetnessSettings.Collector().read("const int "+name+"=10;"));
        }
        shader("gbuffers_terrain.vsh", "const float wetnessHalflife=10.;"+V);
        shader("gbuffers_terrain.fsh", F);
        shader("shadow.vsh", V); shader("shadow.fsh", "const float drynessHalflife=30.;"+F);
        shader("final.fsh", F);
        assertEquals(new WetnessSettings(10, 30), prepare().variants().get("shaders").wetnessSettings());
        shader("final.fsh", "const float wetnessHalflife=11.;"+F);
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void preprocessedIncludesAndDimensionsDoNotLeakSettings() throws Exception {
        shader("settings.glsl", "#define WET 12.0\nconst float wetnessHalflife=WET;\n");
        shader("final.fsh", "#include \"/settings.glsl\"\n#if 0\nconst float wetnessHalflife=99.;\n#endif\n"+F);
        shader("world-1/final.fsh", "const float drynessHalflife=0.;"+F);
        shader("unused.glsl", "const float wetnessHalflife=777.;"+F);
        var prepared = prepare();
        assertEquals(new WetnessSettings(12, 200), prepared.variants().get("shaders").wetnessSettings());
        assertEquals(new WetnessSettings(600, 0), prepared.variants().get("shaders/world-1").wetnessSettings());
    }

    @Test void activePostVertexAndSkyDirectivesContributeToo() throws Exception {
        shader("final.vsh", "const float drynessHalflife=7.;"+V);
        shader("final.fsh", F);
        shader("gbuffers_skybasic.vsh", V);
        shader("gbuffers_skybasic.fsh", "const float wetnessHalflife=15.;"+F);
        var prepared = prepare();
        assertNotNull(prepared.world().skyVariants().get("shaders").programs().get("gbuffers_skybasic").prepared());
        assertEquals(new WetnessSettings(15, 7), prepared.variants().get("shaders").wetnessSettings());
    }

    @Test void sharedVariantStateResetsAndCloseReleasesHistory() {
        Object world = new Object();
        var variant = new PackShaderPipeline.Variant(List.of(), null);
        try (var pack = new PackShaderPipeline("wet-test", Map.of("shaders", variant))) {
            var nativeState = pack.variant("shaders/world-1").wetness();
            assertSame(nativeState, pack.variant("shaders").wetness());
            nativeState.update(world, new Object(), 0, 0);
            var frame = new Object();
            assertEquals(.5f, nativeState.update(world, frame, 1, 60).value());
            pack.resetTemporalHistory();
            assertEquals(1, nativeState.update(world, frame, 1, 0).value());
        }
        assertEquals(0, variant.wetness().update(world, new Object(), 0, 0).value());
    }

    @Test void multiplePostProgramsWriteExactlyTheNativeSampleWithoutResettingSharedState() throws Exception {
        Object world = new Object();
        var shared = new WetnessSmoother(new WetnessSettings(10, 30));
        shared.update(world, AnimationClock.ZERO, 0, 0);
        var frame = new AnimationClock.Snapshot(1, 1, 1);
        var environment = new WorldEnvironment(0, 1);
        var nativeValue = shared.update(world, frame, environment.rainStrength(), frame.frameTime());
        assertEquals(.5f, nativeValue.value());
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class, LegacyFinalShaderAdapter.UniformSemantic.class,
            net.minecraft.client.renderer.state.level.LevelRenderState.class, int.class, int.class,
            WorldEnvironment.class, WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        for (int stage = 0; stage < 4; stage++) {
            try (var program = new PackFinalProgram("test", "composite"+stage, null, List.of(), List.of(), null)) {
                program.prepareWetness(world, frame, environment, shared);
                var bytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                writer.invoke(program, com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
                    LegacyFinalShaderAdapter.UniformSemantic.WETNESS, null, 854, 480, environment, null, frame);
                assertEquals(nativeValue.value(), bytes.getFloat(0));
                assertNotEquals(environment.rainStrength(), bytes.getFloat(0));
                program.resetTemporalHistory();
                assertSame(nativeValue, shared.update(world, frame, 1, 1));
            }
        }
    }

    @Test void nativeStd140LanePreservesSkyAndEarlierOffsetsOnHeapDirectAndSlices() {
        assertEquals(972, NativeWorldBindings.WETNESS_OFFSET);
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        var matrix = new Matrix4f();
        var environment = new WorldEnvironment(3, 1).withSkyColor(new SkyColor(.1f, .2f, .3f));
        var data = new WorldMatrixData(matrix, matrix, matrix, matrix, 1, 2, 3, 854, 480, 6000,
            AnimationClock.ZERO, RelativeEyePosition.ZERO, HeldLight.NONE, environment,
            new EyeBrightnessSmoother.Value(0, 0), CustomUniformBindings.Values.EMPTY, 0, 128,
            NativeFogData.CLEAR, NativeSceneInputs.DEFAULT, new CameraHistory.Position(4, 5, 6),
            HeldItemIds.NONE, .25f);
        for (boolean direct : List.of(false, true)) for (boolean slice : List.of(false, true)) {
            var backing = direct ? ByteBuffer.allocateDirect(1008) : ByteBuffer.allocate(1008);
            backing.order(ByteOrder.nativeOrder());
            for (int i = 0; i < backing.capacity(); i++) backing.put(i, (byte)0x5a);
            int origin = slice ? 8 : 0;
            var target = slice ? backing.position(8).slice() : backing.duplicate();
            target.order(ByteOrder.nativeOrder()).position(8).limit(984);
            data.write(target);
            int base = origin+8;
            assertEquals(.25f, backing.getFloat(base+NativeWorldBindings.WETNESS_OFFSET));
            assertEquals(.1f, backing.getFloat(base+NativeWorldBindings.SKY_COLOR_OFFSET));
            assertEquals(.2f, backing.getFloat(base+NativeWorldBindings.SKY_COLOR_OFFSET+4));
            assertEquals(.3f, backing.getFloat(base+NativeWorldBindings.SKY_COLOR_OFFSET+8));
            assertEquals(8, target.position()); assertEquals(984, target.limit());
            assertEquals(0x5a5a5a5a, backing.getInt(base-4));
            assertEquals(0x5a5a5a5a, backing.getInt(base+NativeWorldBindings.BYTE_SIZE));
        }
    }
}
