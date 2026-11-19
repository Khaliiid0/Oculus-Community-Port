package dev.oculusforge.render.world;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Pure CPU quad enrichment. Vanilla's first 28 bytes are never modified. */
public final class QuadMeshAttributes {
    public static final int STRIDE = 48;
    public static final int NORMAL = 28, TANGENT = 32, MID_UV = 36;
    private static final double EPSILON = 1e-20;
    private static final int[] FINITE_COMPONENTS = {0, 4, 8, 16, 20};

    private QuadMeshAttributes() { }

    public static void fill(ByteBuffer source, int vertices) {
        if (vertices < 0 || vertices % 4 != 0 || (long) vertices * STRIDE != source.remaining())
            throw new IllegalArgumentException("Extended terrain requires complete, exactly sized quads");
        if (source.isReadOnly()) throw new IllegalArgumentException("Extended terrain buffer is read-only");
        ByteBuffer data = source.duplicate().order(ByteOrder.nativeOrder());
        // Validate before writing so a malformed mesh never leaves a partially enriched result.
        for (int v = 0; v < vertices; v++) {
            int p = data.position() + v * STRIDE;
            for (int offset : FINITE_COMPONENTS)
                if (!Float.isFinite(data.getFloat(p + offset)))
                    throw new IllegalArgumentException("Non-finite terrain position or UV");
        }
        for (int v = 0; v < vertices; v += 4) fillQuad(data, data.position() + v * STRIDE);
    }

    private static void fillQuad(ByteBuffer data, int start) {
        // Crossed diagonals give the area-weighted normal of the two quad triangles.
        double ax = component(data, start, 2, 0) - component(data, start, 0, 0);
        double ay = component(data, start, 2, 4) - component(data, start, 0, 4);
        double az = component(data, start, 2, 8) - component(data, start, 0, 8);
        double bx = component(data, start, 3, 0) - component(data, start, 1, 0);
        double by = component(data, start, 3, 4) - component(data, start, 1, 4);
        double bz = component(data, start, 3, 8) - component(data, start, 1, 8);
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        // A zero-area quad has no geometric normal; deterministic finite output avoids NaNs.
        if (length <= EPSILON) { nx = 0; ny = 1; nz = 0; }
        else { nx /= length; ny /= length; nz /= length; }

        double tx = 0, ty = 0, tz = 0, bitX = 0, bitY = 0, bitZ = 0;
        for (int triangle = 1; triangle <= 2; triangle++) {
            int i = triangle, j = triangle + 1;
            double du1 = component(data, start, i, 16) - component(data, start, 0, 16);
            double dv1 = component(data, start, i, 20) - component(data, start, 0, 20);
            double du2 = component(data, start, j, 16) - component(data, start, 0, 16);
            double dv2 = component(data, start, j, 20) - component(data, start, 0, 20);
            double determinant = du1 * dv2 - du2 * dv1;
            if (Math.abs(determinant) <= EPSILON) continue;
            ax = component(data, start, i, 0) - component(data, start, 0, 0);
            ay = component(data, start, i, 4) - component(data, start, 0, 4);
            az = component(data, start, i, 8) - component(data, start, 0, 8);
            bx = component(data, start, j, 0) - component(data, start, 0, 0);
            by = component(data, start, j, 4) - component(data, start, 0, 4);
            bz = component(data, start, j, 8) - component(data, start, 0, 8);
            tx = (ax * dv2 - bx * dv1) / determinant;
            ty = (ay * dv2 - by * dv1) / determinant;
            tz = (az * dv2 - bz * dv1) / determinant;
            bitX = (bx * du1 - ax * du2) / determinant;
            bitY = (by * du1 - ay * du2) / determinant;
            bitZ = (bz * du1 - az * du2) / determinant;
            double dot = tx * nx + ty * ny + tz * nz;
            tx -= nx * dot; ty -= ny * dot; tz -= nz * dot;
            if (tx * tx + ty * ty + tz * tz > EPSILON) break;
        }
        length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (length <= EPSILON) {
            // Degenerate UVs have no unique tangent. Choose a perpendicular unit direction.
            if (Math.abs(ny) < 0.9) { tx = nz; ty = 0; tz = -nx; }
            else { tx = 0; ty = -nz; tz = ny; }
            length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        }
        tx /= length; ty /= length; tz /= length;
        double handedness = (ny * tz - nz * ty) * bitX + (nz * tx - nx * tz) * bitY + (nx * ty - ny * tx) * bitZ;
        double u = 0, w = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            u += component(data, start, vertex, 16) * 0.25;
            w += component(data, start, vertex, 20) * 0.25;
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            int p = start + vertex * STRIDE;
            data.put(p + NORMAL, snorm(nx)); data.put(p + NORMAL + 1, snorm(ny));
            data.put(p + NORMAL + 2, snorm(nz)); data.put(p + NORMAL + 3, (byte) 0);
            data.put(p + TANGENT, snorm(tx)); data.put(p + TANGENT + 1, snorm(ty));
            data.put(p + TANGENT + 2, snorm(tz)); data.put(p + TANGENT + 3, (byte) (handedness < 0 ? -127 : 127));
            data.putFloat(p + MID_UV, (float) u); data.putFloat(p + MID_UV + 4, (float) w);
        }
    }

    private static double component(ByteBuffer data, int start, int vertex, int offset) {
        return data.getFloat(start + vertex * STRIDE + offset);
    }

    private static byte snorm(double value) { return (byte) Math.round(Math.max(-1, Math.min(1, value)) * 127); }
}
