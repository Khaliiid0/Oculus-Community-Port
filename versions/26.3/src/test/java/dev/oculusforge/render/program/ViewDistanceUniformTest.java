package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.List;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ViewDistanceUniformTest {
    private PackFinalProgram program() { return new PackFinalProgram("test","final",null,List.of(),List.of(),null); }

    private float far(PackFinalProgram program,LevelRenderState state) throws Exception {
        var writer=PackFinalProgram.class.getDeclaredMethod("writeUniform",com.mojang.blaze3d.buffers.Std140Builder.class,
            LegacyFinalShaderAdapter.UniformSemantic.class,LevelRenderState.class,int.class,int.class,
            WorldEnvironment.class,WorldClockUniforms.class,AnimationClock.Snapshot.class);
        writer.setAccessible(true);
        var bytes=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        writer.invoke(program,com.mojang.blaze3d.buffers.Std140Builder.intoBuffer(bytes),
            LegacyFinalShaderAdapter.UniformSemantic.FAR,state,960,540,WorldEnvironment.CLEAR,null,null);
        return bytes.getFloat(0);
    }

    @Test void postFarIsRenderDistanceNotTheProjectionClippingPlane() throws Exception {
        var options=new OptionsRenderState();options.renderDistance=8;
        var state=new LevelRenderState();state.cameraRenderState.depthFar=512;
        try(var program=program()) {
            program.prepareViewDistance(options);
            assertEquals(128,far(program,state));
            state.cameraRenderState.depthFar=8192;
            assertEquals(128,far(program,state));
        }
    }

    @Test void postSnapshotAndNativeBufferUseTheSameCapturedEffectiveDistance() throws Exception {
        var options=new OptionsRenderState();options.renderDistance=12;
        var state=new LevelRenderState();state.cameraRenderState.depthFar=768;
        var identity=new Matrix4f();
        var data=new WorldMatrixData(identity,identity,identity,identity,0,0,0,960,540,0,
            AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,WorldEnvironment.CLEAR,
            new EyeBrightnessSmoother.Value(0,0),CustomUniformBindings.Values.EMPTY,0,ShaderViewDistance.blocks(options));
        var nativeBytes=ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE).order(ByteOrder.nativeOrder());
        data.write(nativeBytes);
        try(var program=program()) {
            program.prepareViewDistance(options);
            assertEquals(192,nativeBytes.getFloat(620));
            assertEquals(nativeBytes.getFloat(620),far(program,state));
        }
    }

    @Test void capturedValuesDoNotChangeUntilTheNextPreparationAndResetClearsThem() throws Exception {
        var options=new OptionsRenderState();options.renderDistance=8;
        try(var program=program()) {
            program.prepareViewDistance(options);options.renderDistance=16;
            assertEquals(128,far(program,new LevelRenderState()));
            program.prepareViewDistance(options);
            assertEquals(256,far(program,new LevelRenderState()));
            options.renderDistance=-1;
            assertThrows(IllegalArgumentException.class,()->program.prepareViewDistance(options));
            assertEquals(256,far(program,new LevelRenderState()));
            program.resetTemporalHistory();
            assertEquals(0,far(program,new LevelRenderState()));
        }
    }

    @Test void conversionPreservesZeroAndModdedDistancesWithoutInventingAClipPlane() {
        assertEquals(0,ShaderViewDistance.blocks(0));
        assertEquals(16,ShaderViewDistance.blocks(1));
        assertEquals(16384,ShaderViewDistance.blocks(1024));
        assertTrue(Float.isFinite(ShaderViewDistance.blocks(Integer.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class,()->ShaderViewDistance.blocks(-1));
    }
}
