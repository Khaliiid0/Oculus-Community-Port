package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.program.NativeWorldBindings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

final class WorldMatrixDataTest {
    @Test void zeroFovStartupDefersAndValidReverseDepthCameraRecovers() {
        var view = new Matrix4f().rotateX(.6f).rotateY(-1.2f);
        for (boolean zeroToOne : new boolean[] {false, true}) {
            var projection = new Matrix4f().perspective(0, 1.7f, 2048f, .05f, zeroToOne);
            assertFalse(WorldMatrixData.cameraReady(projection, view, 1, 80, -2));
            projection.setPerspective(1.2f, 1.7f, 2048f, .05f, zeroToOne);
            assertTrue(WorldMatrixData.cameraReady(projection, view, 1, 80, -2));
            var data = new WorldMatrixData(projection, view, new Matrix4f(), new Matrix4f(), 1, 80, -2, 854, 480);
            var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE).order(ByteOrder.nativeOrder());
            data.write(bytes);
            assertTrue(matrix(bytes, 0).mul(matrix(bytes, 64)).equals(new Matrix4f(), .0001f));
            projection.zero();
            assertFalse(WorldMatrixData.cameraReady(projection, view, 1, 80, -2));
        }
    }

    @Test void readinessRejectsInvalidCameraWithoutMutatingInputs() {
        var valid = new Matrix4f();
        for (var bad : new Matrix4f[] {new Matrix4f().zero(), new Matrix4f().m00(Float.NaN), new Matrix4f().m00(Float.POSITIVE_INFINITY)}) {
            assertFalse(WorldMatrixData.cameraReady(bad, valid, 0, 0, 0));
            assertFalse(WorldMatrixData.cameraReady(valid, bad, 0, 0, 0));
        }
        assertFalse(WorldMatrixData.cameraReady(valid, valid, Double.MAX_VALUE, 0, 0));
        assertFalse(WorldMatrixData.cameraReady(valid, valid, 0, Double.NaN, 0));
        assertFalse(WorldMatrixData.cameraReady(valid, valid, 0, 0, Double.POSITIVE_INFINITY));
        assertTrue(WorldMatrixData.cameraReady(valid, valid, 0, 0, 0));
        assertEquals(new Matrix4f(), valid);
    }

    private static Matrix4f matrix(ByteBuffer bytes, int offset) {
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) values[i] = bytes.getFloat(offset + i * 4);
        return new Matrix4f().set(values);
    }
    @Test void std140OffsetsAndInversesPreserveActualTransforms() {
        var projection = new Matrix4f().perspective(1.2f, 1.7f, 0.05f, 1024f);
        var view = new Matrix4f().rotateX(.3f).rotateY(-.8f);
        var shadowProjection = new Matrix4f().ortho(-64, 64, -64, 64, -256, 256);
        var shadowView = new Matrix4f().rotateX(1.1f).rotateY(.7f);
        var data = new WorldMatrixData(projection, view, shadowProjection, shadowView, -30.5, 82.25, 14.75, 854, 480);
        var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE).order(ByteOrder.nativeOrder());
        data.write(bytes);
        Matrix4f[] expected = {projection, view, shadowProjection, shadowView};
        for (int i = 0; i < 4; i++) {
            var actual = matrix(bytes, i * 128);
            var inverse = matrix(bytes, i * 128 + 64);
            assertTrue(actual.equals(expected[i], 0.00001f));
            assertTrue(actual.mul(inverse).equals(new Matrix4f(), 0.0001f));
        }
        assertEquals(-30.5f, bytes.getFloat(512));
        assertEquals(82.25f, bytes.getFloat(516));
        assertEquals(14.75f, bytes.getFloat(520));
        assertEquals(1f, bytes.getFloat(524));
        var point = new Vector4f(4, 8, -16, 1);
        var expectedPosition = new Matrix4f(shadowProjection).mul(shadowView).transform(new Vector4f(point));
        var separate = matrix(bytes, 256).mul(matrix(bytes, 384)).transform(point);
        assertTrue(expectedPosition.equals(separate, 0.00001f));
    }

    @Test void snapshotsAreImmutableAndWritesRespectSlices() {
        var matrix = new Matrix4f();
        var data = new WorldMatrixData(matrix, matrix, matrix, matrix, 1, 2, 3, 854, 480);
        matrix.zero();
        var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16);
        bytes.position(8).limit(bytes.capacity() - 8);
        data.write(bytes);
        assertEquals(8, bytes.position());
        assertEquals(ByteOrder.BIG_ENDIAN, bytes.order());
        assertEquals(1, bytes.duplicate().order(ByteOrder.nativeOrder()).getFloat(8));
        assertEquals(0, bytes.getInt(0));
        assertEquals(0, bytes.duplicate().clear().getInt(bytes.limit()));
    }

    @Test void rejectsSingularNonFiniteAndBadDestinations() {
        for (var bad : new Matrix4f[] {new Matrix4f().zero(), new Matrix4f().m00(Float.NaN), new Matrix4f().m00(Float.POSITIVE_INFINITY)})
            assertThrows(IllegalArgumentException.class, () -> new WorldMatrixData(bad, new Matrix4f(), new Matrix4f(), new Matrix4f(), 0, 0, 0, 854, 480));
        assertThrows(IllegalArgumentException.class, () -> new WorldMatrixData(new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), Double.MAX_VALUE, 0, 0, 854, 480));
        var valid = new WorldMatrixData(new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), 0, 0, 0, 854, 480);
        assertThrows(IllegalArgumentException.class, () -> valid.write(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE - 1)));
        assertThrows(IllegalArgumentException.class, () -> valid.write(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE).asReadOnlyBuffer()));
    }

    @Test void directAndHeapBuffersHaveIdenticalBoundedPayloads() {
        var transform = new Matrix4f().translate(1, 2, 3).rotateY(.4f);
        var data = new WorldMatrixData(transform, transform, transform, transform, -5, 6, 7, 854, 480);
        var heap = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE);
        var direct = ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE);
        data.write(heap); data.write(direct);
        for (int i = 0; i < NativeWorldBindings.BYTE_SIZE; i++) assertEquals(heap.get(i), direct.get(i));
    }

    @Test void viewportPayloadTracksResizeWithoutChangingPreviousSnapshot() {
        var identity = new Matrix4f();
        var first = new WorldMatrixData(identity, identity, identity, identity, 0, 0, 0, 854, 480);
        for (int[] size : new int[][] {{854, 480}, {1920, 1080}, {480, 854}, {1, 1}}) {
            var data = new WorldMatrixData(identity, identity, identity, identity, 0, 0, 0, size[0], size[1]);
            var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE).order(ByteOrder.nativeOrder());
            data.write(bytes);
            assertEquals((float) size[0], bytes.getFloat(528));
            assertEquals((float) size[1], bytes.getFloat(532));
            assertEquals((float) size[0] / size[1], bytes.getFloat(536));
            assertEquals(0f, bytes.getFloat(540));
            first.write(bytes);
            assertEquals(854f, bytes.getFloat(528));
            assertEquals(480f, bytes.getFloat(532));
        }
    }

    @Test void zeroOrNegativeViewportIsRejected() {
        var identity = new Matrix4f();
        for (int[] size : new int[][] {{0, 480}, {854, 0}, {-1, 480}, {854, -1}})
            assertThrows(IllegalArgumentException.class, () -> new WorldMatrixData(identity, identity,
                identity, identity, 0, 0, 0, size[0], size[1]));
    }
}
