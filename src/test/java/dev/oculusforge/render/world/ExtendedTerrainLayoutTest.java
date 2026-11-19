package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
}
