package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class QuadMeshAttributesTest {
    private static final float[][] SQUARE = {{0,0,0,0,0}, {1,0,0,1,0}, {1,1,0,1,1}, {0,1,0,0,1}};

    private static ByteBuffer mesh(float[][] vertices) {
        ByteBuffer data = ByteBuffer.allocate(vertices.length * QuadMeshAttributes.STRIDE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < vertices.length; i++) {
            int p = i * QuadMeshAttributes.STRIDE;
            for (int c = 0; c < 3; c++) data.putFloat(p + c * 4, vertices[i][c]);
            data.putInt(p + 12, 0xAABBCCDD);
            data.putFloat(p + 16, vertices[i][3]); data.putFloat(p + 20, vertices[i][4]);
            data.putInt(p + 24, 0x00F00080);
            data.putInt(p + 44, -12345);
        }
        return data;
    }

    private static void vector(ByteBuffer data, int offset, double x, double y, double z) {
        assertEquals(x, data.get(offset) / 127.0, 0.01);
        assertEquals(y, data.get(offset + 1) / 127.0, 0.01);
        assertEquals(z, data.get(offset + 2) / 127.0, 0.01);
    }

    @Test void derivesNormalTangentHandednessAndMidUvWithoutChangingVanillaBytes() {
        ByteBuffer data = mesh(SQUARE);
        byte[] before = data.array().clone();
        QuadMeshAttributes.fill(data, 4);
        for (int i = 0; i < 4; i++) {
            int p = i * QuadMeshAttributes.STRIDE;
            for (int b = 0; b < 28; b++) assertEquals(before[p+b], data.get(p+b));
            vector(data, p + 28, 0, 0, 1); vector(data, p + 32, 1, 0, 0);
            assertEquals(127, data.get(p + 35));
            assertEquals(0.5f, data.getFloat(p + 36)); assertEquals(0.5f, data.getFloat(p + 40));
            assertEquals(-12345, data.getInt(p + 44), "Geometry enrichment must preserve material state identity");
        }
    }

    @Test void handlesEveryAxisAndReversedWinding() {
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[] {-1, 1}) {
            float[][] vertices = new float[4][5];
            for (int i = 0; i < 4; i++) {
                float[] input = SQUARE[sign == 1 ? i : 3 - i];
                vertices[i][axis] = input[0]; vertices[i][(axis + 1) % 3] = input[1];
                vertices[i][3] = input[3]; vertices[i][4] = input[4];
            }
            ByteBuffer data = mesh(vertices); QuadMeshAttributes.fill(data, 4);
            double[] expected = new double[3]; expected[(axis + 2) % 3] = sign;
            vector(data, 28, expected[0], expected[1], expected[2]);
        }
    }

    @Test void mirroredUvFlipsTangentAndHandednessButNotNormal() {
        ByteBuffer data = mesh(SQUARE);
        for (int i = 0; i < 4; i++) data.putFloat(i * 48 + 16, 1 - data.getFloat(i * 48 + 16));
        QuadMeshAttributes.fill(data, 4);
        vector(data, 28, 0, 0, 1); vector(data, 32, -1, 0, 0);
        assertEquals(-127, data.get(35));
    }

    @Test void degenerateUvsUseFiniteOrthogonalFallback() {
        ByteBuffer data = mesh(SQUARE);
        for (int i = 0; i < 4; i++) { data.putFloat(i * 48 + 16, 0); data.putFloat(i * 48 + 20, 0); }
        QuadMeshAttributes.fill(data, 4);
        vector(data, 28, 0, 0, 1);
        assertEquals(0, data.get(34));
        assertTrue(Math.abs(data.get(32)) + Math.abs(data.get(33)) >= 127);
    }

    @Test void usesSecondTriangleWhenFirstTriangleUvIsDegenerate() {
        ByteBuffer data = mesh(SQUARE); data.putFloat(48 + 16, 0);
        QuadMeshAttributes.fill(data, 4);
        vector(data, 32, 1, 0, 0);
    }

    @Test void zeroAreaGeometryHasDeterministicFiniteFrame() {
        ByteBuffer data = mesh(new float[4][5]); QuadMeshAttributes.fill(data, 4);
        vector(data, 28, 0, 1, 0); vector(data, 32, 0, 0, 1);
        assertEquals(127, data.get(35));
    }

    @Test void nonPlanarQuadUsesAreaWeightedNormalAndOrthogonalTangent() {
        ByteBuffer data = mesh(SQUARE); data.putFloat(2 * 48 + 8, 1);
        QuadMeshAttributes.fill(data, 4);
        vector(data, 28, -1 / Math.sqrt(6), -1 / Math.sqrt(6), 2 / Math.sqrt(6));
        double dot = 0; for (int c = 0; c < 3; c++) dot += data.get(28+c) * data.get(32+c) / (127.0 * 127.0);
        assertEquals(0, dot, 0.02);
    }

    @Test void respectsBufferSliceAndDoesNotMutatePositionLimitOrOrder() {
        ByteBuffer expected = mesh(SQUARE);
        ByteBuffer data = ByteBuffer.allocateDirect(expected.remaining() + 16);
        data.position(8); data.put(expected); data.limit(data.position()); data.position(8);
        ByteOrder originalOrder = data.order(); int limit = data.limit();
        QuadMeshAttributes.fill(data, 4);
        assertEquals(8, data.position()); assertEquals(limit, data.limit()); assertEquals(originalOrder, data.order());
        vector(data, 8 + 28, 0, 0, 1);
        assertEquals(0, data.get(0));
    }

    @Test void slopedFluidLikeQuadsStayWithinSnormQuantizationBounds() {
        var random = new java.util.Random(17);
        double componentError = 0.5 / 127.0;
        double vectorError = Math.sqrt(3) * componentError;
        double dotError = 2 * vectorError + vectorError * vectorError;
        boolean exceedsOldCombinedProbeTolerance = false;
        for (int sample = 0; sample < 4000; sample++) {
            float[][] vertices = {{0,random.nextFloat(),0,0,0}, {0,random.nextFloat(),1,0,1},
                {1,random.nextFloat(),1,1,1}, {1,random.nextFloat(),0,1,0}};
            double angle = random.nextDouble() * Math.PI * 2;
            for (float[] vertex : vertices) {
                double u = vertex[3], v = vertex[4];
                vertex[3] = (float) (u * Math.cos(angle) - v * Math.sin(angle));
                vertex[4] = (float) (u * Math.sin(angle) + v * Math.cos(angle));
            }
            var data = mesh(vertices);
            QuadMeshAttributes.fill(data, 4);
            double normalLength2 = 0, tangentLength2 = 0, dot = 0;
            for (int axis = 0; axis < 3; axis++) {
                double n = data.get(28 + axis) / 127.0, t = data.get(32 + axis) / 127.0;
                normalLength2 += n * n; tangentLength2 += t * t; dot += n * t;
            }
            double normalError = Math.abs(Math.sqrt(normalLength2) - 1);
            assertTrue(normalError <= vectorError + 1e-6);
            assertTrue(Math.abs(Math.sqrt(tangentLength2) - 1) <= vectorError + 1e-6);
            assertTrue(Math.abs(dot) <= dotError + 1e-6);
            exceedsOldCombinedProbeTolerance |= normalError + Math.abs(dot) > .005;
        }
        assertTrue(exceedsOldCombinedProbeTolerance, "A float-matrix tolerance is too strict for combined SNORM8 errors");
    }

    @Test void rejectsIncompleteWrongSizedReadOnlyOrNonFiniteMeshesBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> QuadMeshAttributes.fill(mesh(SQUARE), 3));
        assertThrows(IllegalArgumentException.class, () -> QuadMeshAttributes.fill(mesh(SQUARE), Integer.MAX_VALUE - 3));
        assertThrows(IllegalArgumentException.class, () -> QuadMeshAttributes.fill(mesh(SQUARE).asReadOnlyBuffer(), 4));
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            ByteBuffer data = mesh(SQUARE); data.putFloat(3 * 48 + 16, invalid);
            byte[] before = data.array().clone();
            assertThrows(IllegalArgumentException.class, () -> QuadMeshAttributes.fill(data, 4));
            assertArrayEquals(before, data.array());
        }
    }

    @Test void eachQuadIsIndependentAndEmptyMeshIsSafe() {
        ByteBuffer data = ByteBuffer.allocate(8 * 48).order(ByteOrder.nativeOrder());
        data.put(mesh(SQUARE)); data.put(mesh(new float[4][5])); data.flip();
        QuadMeshAttributes.fill(data, 8);
        vector(data, 28, 0, 0, 1); vector(data, 4 * 48 + 28, 0, 1, 0);
        QuadMeshAttributes.fill(ByteBuffer.allocate(0), 0);
    }
}
