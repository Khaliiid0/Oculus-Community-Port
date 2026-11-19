package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static dev.oculusforge.render.program.LegacyFinalShaderAdapter.UniformSemantic.*;

final class PostCameraHistoryTest {
    @Test void nativeAndTwoPostStagesKeepTheSamePreviousCameraAndDoubleFractions() throws Exception {
        var history=new CameraHistory();var world=new Object();var frame=new Object();
        history.update(world,new Object(),30_000_000.125,-3.25,7.875);
        var nativeSnapshot=history.update(world,frame,30_000_003.5,2.5,9.25);
        var state=new LevelRenderState();state.cameraRenderState.pos=new Vec3(30_000_003.5,2.5,9.25);
        try(var a=program();var b=program()) {
            for(var stage:List.of(a,b,a)) {
                stage.prepareCamera(world,frame,state.cameraRenderState.pos.x,state.cameraRenderState.pos.y,state.cameraRenderState.pos.z,history);
                vector(write(stage,state,PREVIOUS_CAMERA_POSITION),0,(float)30_000_000.125,-3.25f,7.875f);
                vector(write(stage,state,PREVIOUS_CAMERA_POSITION_FRACT),0,.125f,.75f,.875f);
            }
            assertSame(nativeSnapshot,history.update(world,frame,30_000_003.5,2.5,9.25));
        }
    }

    @Test void variantResetClearsSharedCameraStateEvenForPostOnlyPacks() {
        var variant=new PackShaderPipeline.Variant(List.of(),null);
        var world=new Object();variant.cameraHistory().update(world,new Object(),1,2,3);
        try(var pipeline=new PackShaderPipeline("history",java.util.Map.of("shaders",variant))) {
            pipeline.resetTemporalHistory();
            var fresh=variant.cameraHistory().update(world,new Object(),4,5,6);
            assertSame(fresh.position(),fresh.previous());
        }
    }
    @Test void firstFrameProbeRetainsMarkerAndOmitsFullPreviousPosition() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("first-frame-camera").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("first-frame-camera", "First frame", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        assertNotNull(prepared.world().variant("shaders").nativeTerrain());
        var variant = prepared.variants().get("shaders");
        assertTrue(variant.buffers().retainedColorSlots().containsKey(7));
        var semantics = prepared.programs().stream().flatMap(p -> p.fragment().uniforms().stream())
            .map(LegacyFinalShaderAdapter.UniformBinding::semantic).toList();
        assertTrue(semantics.containsAll(List.of(PREVIOUS_CAMERA_POSITION_FRACT, CAMERA_POSITION_FRACT,
            CAMERA_VELOCITY, GBUFFER_PREVIOUS_PROJECTION, GBUFFER_PREVIOUS_MODEL_VIEW)));
        assertFalse(semantics.contains(PREVIOUS_CAMERA_POSITION));
    }

    private PackFinalProgram program() {
        return new PackFinalProgram("test", "final", null, List.of(), List.of(), null);
    }

    private void set(PackFinalProgram program, String name, Object value) throws Exception {
        var field = PackFinalProgram.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(program, value);
    }

    private ByteBuffer write(PackFinalProgram program, LevelRenderState state,
        LegacyFinalShaderAdapter.UniformSemantic... semantics) throws Exception {
        var writer = PackFinalProgram.class.getDeclaredMethod("writeUniform",
            com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class, LevelRenderState.class,
            int.class, int.class, dev.oculusforge.render.world.WorldEnvironment.class,
            WorldClockUniforms.class, AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        var bytes = ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder());
        var builder = com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes);
        for (var semantic : semantics) writer.invoke(program, builder, semantic, state, 854, 480,
            dev.oculusforge.render.world.WorldEnvironment.CLEAR, null, null);
        return bytes;
    }

    private void vector(ByteBuffer bytes, int offset, float x, float y, float z) {
        assertEquals(x, bytes.getFloat(offset), .000001f);
        assertEquals(y, bytes.getFloat(offset + 4), .000001f);
        assertEquals(z, bytes.getFloat(offset + 8), .000001f);
    }

    private void matrix(ByteBuffer bytes, Matrix4f expected) {
        float[] values = new float[16];
        expected.get(values);
        for (int i = 0; i < values.length; i++) assertEquals(values[i], bytes.getFloat(i * 4), .000001f);
    }

    @Test void firstFrameFractionDoesNotRequireFullPreviousPosition() throws Exception {
        var state = new LevelRenderState();
        state.cameraRenderState.pos = new Vec3(12.25, -3.25, -.125);
        try (var program = program()) {
            vector(write(program, state, PREVIOUS_CAMERA_POSITION_FRACT), 0, .25f, .75f, .875f);
        }
    }

    @Test void firstFrameOrderAndResetDoNotChangePositionOrVelocity() throws Exception {
        var state = new LevelRenderState();
        state.cameraRenderState.pos = new Vec3(12.25, -3.25, -.125);
        try (var program = program()) {
            for (boolean fractionFirst : new boolean[] {true, false}) {
                program.resetTemporalHistory();
                set(program, "lastFrameTime", .1f);
                var bytes = write(program, state,
                    fractionFirst ? PREVIOUS_CAMERA_POSITION_FRACT : PREVIOUS_CAMERA_POSITION,
                    fractionFirst ? PREVIOUS_CAMERA_POSITION : PREVIOUS_CAMERA_POSITION_FRACT,
                    CAMERA_VELOCITY);
                vector(bytes, fractionFirst ? 0 : 16, .25f, .75f, .875f);
                vector(bytes, fractionFirst ? 16 : 0, 12.25f, -3.25f, -.125f);
                assertEquals(0f, bytes.getFloat(32));
            }
        }
    }

    @Test void existingHistorySuppliesOldPositionAndRealMotionUntilReset() throws Exception {
        var state = new LevelRenderState();
        state.cameraRenderState.pos = new Vec3(4.25, 6.5, 3.75);
        try (var program = program()) {
            set(program, "previousCameraX", 1.25d);
            set(program, "previousCameraY", 2.5d);
            set(program, "previousCameraZ", 3.75d);
            set(program, "hasPreviousCamera", true);
            set(program, "lastFrameTime", .5f);
            vector(write(program, state, PREVIOUS_CAMERA_POSITION), 0, 1.25f, 2.5f, 3.75f);
            vector(write(program, state, PREVIOUS_CAMERA_POSITION_FRACT), 0, .25f, .5f, .75f);
            assertEquals(10f, write(program, state, CAMERA_VELOCITY).getFloat(0));
            set(program, "lastFrameTime", 0f);
            assertEquals(0f, write(program, state, CAMERA_VELOCITY).getFloat(0));
            state.cameraRenderState.pos = new Vec3(4.5, 6.75, 3.875);
            vector(write(program, state, PREVIOUS_CAMERA_POSITION_FRACT), 0, .25f, .5f, .75f);
            program.resetTemporalHistory();
            vector(write(program, state, PREVIOUS_CAMERA_POSITION), 0, 4.5f, 6.75f, 3.875f);
        }
    }

    @Test void previousMatricesUseCurrentOnFirstFrameAndStoredHistoryAfterward() throws Exception {
        var state = new LevelRenderState();
        state.cameraRenderState.projectionMatrix = new Matrix4f().perspective(1.1f, 1.5f, .05f, 128);
        state.cameraRenderState.viewRotationMatrix = new Matrix4f().rotateXYZ(.2f, -.3f, .4f);
        var oldProjection = new Matrix4f().perspective(.9f, 1.8f, .1f, 256);
        var oldView = new Matrix4f().rotateXYZ(-.5f, .1f, -.2f);
        try (var program = program()) {
            for (boolean hasHistory : new boolean[] {false, true, false}) {
                if (hasHistory) {
                    set(program, "previousProjection", oldProjection);
                    set(program, "previousModelView", oldView);
                } else program.resetTemporalHistory();
                var projection = LegacyProjection.fromReverseDepth(
                    hasHistory ? oldProjection : state.cameraRenderState.projectionMatrix, false);
                var view = hasHistory ? oldView : state.cameraRenderState.viewRotationMatrix;
                matrix(write(program, state, GBUFFER_PREVIOUS_PROJECTION), projection);
                matrix(write(program, state, GBUFFER_PREVIOUS_PROJECTION_INVERSE), new Matrix4f(projection).invert());
                matrix(write(program, state, GBUFFER_PREVIOUS_MODEL_VIEW), view);
                matrix(write(program, state, GBUFFER_PREVIOUS_MODEL_VIEW_INVERSE), new Matrix4f(view).invert());
            }
        }
    }
}
