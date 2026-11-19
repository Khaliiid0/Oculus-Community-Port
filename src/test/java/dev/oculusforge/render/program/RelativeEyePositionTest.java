package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import dev.oculusforge.render.world.RelativeEyePosition;
import dev.oculusforge.render.world.WorldMatrixData;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class RelativeEyePositionTest {
    @Test void diagnosticPackPreparesBothNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("relative-eye").toURI());
        var pack = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("eye", "Eye probe", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = pack.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertTrue(world.nativeTerrain().usesWorldMatrices());
        assertTrue(world.nativeShadow().usesWorldMatrices());
    }
    @Test void subtractionPreservesSignAndFarOriginPrecision() {
        assertEquals(RelativeEyePosition.ZERO, RelativeEyePosition.between(1, 2, 3, 1, 2, 3));
        assertEquals(new RelativeEyePosition(.25f, -.125f, -4),
            RelativeEyePosition.between(30_000_000.25, 80.375, -30_000_004,
                30_000_000, 80.5, -30_000_000));
        assertEquals(new RelativeEyePosition(0, 0, 4), RelativeEyePosition.between(0, 2, 4, 0, 2, 0));
    }
    @Test void invalidCoordinatesAreNeverUploaded() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> RelativeEyePosition.between(bad, 0, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> RelativeEyePosition.between(0, 0, 0, 0, bad, 0));
        }
    }
    @Test void std140AppendsVectorWithoutMovingClocksAndPreservesBounds() {
        var m = new Matrix4f();
        var data = new WorldMatrixData(m, m, m, m, 10, 20, 30, 854, 480, 24007,
            new AnimationClock.Snapshot(42, .01f, 2), new RelativeEyePosition(1, -2, 3));
        var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16).order(ByteOrder.nativeOrder());
        bytes.position(8).limit(NativeWorldBindings.BYTE_SIZE + 8);
        data.write(bytes);
        assertEquals(7, bytes.getInt(8 + 540));
        assertEquals(1, bytes.getInt(8 + 544));
        assertEquals(42, bytes.getInt(8 + 548));
        assertEquals(1f, bytes.getFloat(8 + 560));
        assertEquals(-2f, bytes.getFloat(8 + 564));
        assertEquals(3f, bytes.getFloat(8 + 568));
        assertEquals(0f, bytes.getFloat(8 + 572));
        assertEquals(8, bytes.position());
        assertEquals(0, bytes.getInt(0));
        assertEquals(0, bytes.duplicate().clear().getInt(NativeWorldBindings.BYTE_SIZE + 8));
    }
    @Test void vectorBindsInBothNativeStagesAndRejectsWrongDeclarations() throws Exception {
        for (boolean shadow : new boolean[] {false, true}) {
            var p = NativeTerrainProgram.adaptWithMesh(
                "uniform vec3 relativeEyePosition; void main(){gl_Position=ftransform()+vec4(relativeEyePosition,0);}",
                "uniform vec3 relativeEyePosition; void main(){gl_FragColor=vec4(relativeEyePosition,1);}",
                List.of(0), shadow, false);
            for (String s : List.of(p.vertexSource(), p.fragmentSource()))
                assertTrue(s.contains("vec4(oculusNative_relativeEyePosition.xyz,"));
            assertTrue(p.usesWorldMatrices());
        }
        for (String declaration : List.of("uniform vec4 relativeEyePosition;", "uniform vec3 relativeEyePosition[2];",
            "uniform vec3 relativeEyePosition; uniform vec3 relativeEyePosition;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                declaration + "void main(){gl_Position=ftransform();}", "void main(){gl_FragColor=vec4(1);}", List.of(0)));
    }
}
