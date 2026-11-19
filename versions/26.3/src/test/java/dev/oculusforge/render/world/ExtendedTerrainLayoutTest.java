package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class ExtendedTerrainLayoutTest {
    @Test void preservesBaseOffsetsAndVanillaFormatIdentity() {
        assertEquals(28, DefaultVertexFormat.BLOCK.getVertexSize());
        assertEquals(48, ExtendedTerrainMesh.FORMAT.getVertexSize());
        for (var element : DefaultVertexFormat.BLOCK.getElements())
            assertEquals(element, ExtendedTerrainMesh.FORMAT.getElement(element.name()));
        assertEquals(28, ExtendedTerrainMesh.FORMAT.getElement("OculusNormal").offset());
        assertEquals(GpuFormat.RGBA8_SNORM, ExtendedTerrainMesh.FORMAT.getElement("OculusTangent").format());
        assertEquals(32, ExtendedTerrainMesh.FORMAT.getElement("OculusTangent").offset());
        assertEquals(36, ExtendedTerrainMesh.FORMAT.getElement("OculusMidUV").offset());
        assertEquals(44, ExtendedTerrainMesh.FORMAT.getElement("OculusState").offset());
        assertEquals(GpuFormat.R32_SINT, ExtendedTerrainMesh.FORMAT.getElement("OculusState").format());
    }

    @Test void actualBufferBuilderProducesEnrichableMeshWithBulkAndFluidStyleWriters() {
        try (var storage = new ByteBufferBuilder(1024)) {
            var builder = new BufferBuilder(storage, PrimitiveTopology.QUADS, ExtendedTerrainMesh.FORMAT);
            builder.addVertex(0,0,0, -1, 0,0, 0, 0, 0,0,1);
            builder.addVertex(1,0,0).setColor(-1).setUv(1,0).setUv2(0,0);
            builder.addVertex(1,1,0, -1, 1,1, 0, 0, 0,0,1);
            builder.addVertex(0,1,0).setColor(-1).setUv(0,1).setUv2(0,0);
            try (var mesh = builder.buildOrThrow()) {
                assertEquals(192, mesh.vertexBuffer().remaining());
                QuadMeshAttributes.fill(mesh.vertexBuffer(), mesh.drawState().vertexCount());
                assertEquals(127, mesh.vertexBuffer().get(30));
                assertEquals(0.5f, mesh.vertexBuffer().getFloat(36));
            }
        }
    }

    @Test void actualWritersPreserveIndependentQuadFramesExactMidUvsAndMaterialBytes() {
        float[][][] quads = {
            {{0,0,0,.125f,.25f}, {1,0,0,.375f,.25f}, {1,1,0,.375f,.75f}, {0,1,0,.125f,.75f}},
            {{0,0,0,.75f,.25f}, {1,1,0,.5f,.25f}, {1,1,1,.5f,.5f}, {0,0,1,.75f,.5f}},
            {{0,0,0,.75f,.5f}, {0,1,0,.875f,.5f}, {0,1,1,.875f,.75f}, {0,0,1,.75f,.75f}}
        };
        double diagonal = Math.sqrt(.5);
        double[][] normals = {{0,0,1}, {diagonal,-diagonal,0}, {1,0,0}};
        double[][] tangents = {{1,0,0}, {-diagonal,-diagonal,0}, {0,1,0}};
        float[][] midUvs = {{.25f,.5f}, {.625f,.375f}, {.8125f,.625f}};
        int[] materialBytes = {421, -901, 0};

        // Small initial storage also exercises the actual writer's growth path.
        try (var storage = new ByteBufferBuilder(128)) {
            var builder = new BufferBuilder(storage, PrimitiveTopology.QUADS, ExtendedTerrainMesh.FORMAT);
            for (int q = 0; q < quads.length; q++) appendQuad(builder, quads[q], q);
            try (var mesh = builder.buildOrThrow()) {
                var data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                int start = data.position();
                assertEquals(12, mesh.drawState().vertexCount());
                assertEquals(12 * QuadMeshAttributes.STRIDE, data.remaining());
                // State capture is a separate mixin contract. Here, seed its bytes and
                // prove geometry enrichment neither rewrites nor crosses quad identity.
                for (int v = 0; v < 12; v++)
                    data.putInt(start + v * QuadMeshAttributes.STRIDE + 44, materialBytes[v / 4]);
                byte[] before = snapshot(data);
                QuadMeshAttributes.fill(data, mesh.drawState().vertexCount());
                for (int v = 0; v < 12; v++) {
                    int p = start + v * QuadMeshAttributes.STRIDE, q = v / 4;
                    for (int b = 0; b < 28; b++)
                        assertEquals(before[v * QuadMeshAttributes.STRIDE + b], data.get(p + b));
                    assertSnorm(data, p + QuadMeshAttributes.NORMAL, normals[q]);
                    assertSnorm(data, p + QuadMeshAttributes.TANGENT, tangents[q]);
                    assertEquals(q == 1 ? -127 : 127, data.get(p + QuadMeshAttributes.TANGENT + 3));
                    assertEquals(midUvs[q][0], data.getFloat(p + QuadMeshAttributes.MID_UV));
                    assertEquals(midUvs[q][1], data.getFloat(p + QuadMeshAttributes.MID_UV + 4));
                    assertEquals(materialBytes[q], data.getInt(p + 44));
                }
            }
        }
    }

    @Test void invalidLaterQuadCannotPartiallyEnrichAnActualWriterMesh() {
        float[][] square = {{0,0,0,0,0}, {1,0,0,1,0}, {1,1,0,1,1}, {0,1,0,0,1}};
        for (int component : new int[] {0,4,8,16,20}) {
            try (var storage = new ByteBufferBuilder(128)) {
                var builder = new BufferBuilder(storage, PrimitiveTopology.QUADS, ExtendedTerrainMesh.FORMAT);
                appendQuad(builder, square, 0);
                appendQuad(builder, square, 1);
                try (var mesh = builder.buildOrThrow()) {
                    var data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                    data.putFloat(data.position() + 7 * QuadMeshAttributes.STRIDE + component, Float.NaN);
                    byte[] before = snapshot(data);
                    assertThrows(IllegalArgumentException.class,
                        () -> QuadMeshAttributes.fill(data, mesh.drawState().vertexCount()));
                    assertArrayEquals(before, snapshot(data), "Validation must precede all mesh writes");
                }
            }
        }
    }

    private static void appendQuad(BufferBuilder builder, float[][] vertices, int quad) {
        for (int i = 0; i < vertices.length; i++) {
            float[] v = vertices[i];
            int color = 0xff123456 + quad * 16 + i;
            if ((i + quad) % 2 == 0)
                builder.addVertex(v[0],v[1],v[2], color, v[3],v[4], 0, 0x00f00080, 0,0,1);
            else builder.addVertex(v[0],v[1],v[2]).setColor(color).setUv(v[3],v[4]).setUv2(128,240);
        }
    }

    private static byte[] snapshot(ByteBuffer data) {
        byte[] result = new byte[data.remaining()];
        data.duplicate().get(result);
        return result;
    }

    private static void assertSnorm(ByteBuffer data, int offset, double[] expected) {
        for (int axis = 0; axis < 3; axis++)
            assertEquals(expected[axis], data.get(offset + axis) / 127.0, .5 / 127 + 1e-6);
    }
}
