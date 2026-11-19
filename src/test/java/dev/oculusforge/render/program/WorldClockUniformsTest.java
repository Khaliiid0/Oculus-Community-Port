package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import dev.oculusforge.render.world.WorldMatrixData;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class WorldClockUniformsTest {
    @Test void dayBoundaryAndNegativeClockValuesNormalize() {
        assertEquals(new WorldClockUniforms(0, 0), WorldClockUniforms.fromTicks(0));
        assertEquals(new WorldClockUniforms(23999, 0), WorldClockUniforms.fromTicks(23999));
        assertEquals(new WorldClockUniforms(0, 1), WorldClockUniforms.fromTicks(24000));
        assertEquals(new WorldClockUniforms(23999, -1), WorldClockUniforms.fromTicks(-1));
        assertEquals(new WorldClockUniforms(0, -1), WorldClockUniforms.fromTicks(-24000));
    }

    @Test void extremeDaysSaturateInsteadOfIntegerWrapping() {
        assertEquals(Integer.MAX_VALUE, WorldClockUniforms.fromTicks(Long.MAX_VALUE).day());
        assertEquals(Integer.MIN_VALUE, WorldClockUniforms.fromTicks(Long.MIN_VALUE).day());
        for (long ticks : new long[] {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 24000}) {
            int time = WorldClockUniforms.fromTicks(ticks).time();
            assertTrue(time >= 0 && time < 24000);
        }
    }

    @Test void nativePayloadUsesIntegerPaddingSlotAndHandlesClockResets() {
        var identity = new Matrix4f();
        var bytes = ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16).order(ByteOrder.nativeOrder());
        bytes.position(8).limit(bytes.capacity() - 8);
        for (long ticks : new long[] {6000, 6000, 23999, 24000, 1000, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            new WorldMatrixData(identity, identity, identity, identity, 1, 2, 3, 854, 480, ticks).write(bytes);
            assertEquals(WorldClockUniforms.fromTicks(ticks).time(), bytes.getInt(8 + 540));
            assertEquals(WorldClockUniforms.fromTicks(ticks).day(), bytes.getInt(8 + 544));
            assertEquals(854f, bytes.getFloat(8 + 528));
            assertEquals(480f, bytes.getFloat(8 + 532));
            assertEquals(8, bytes.position());
            assertEquals(0, bytes.getInt(0));
            assertEquals(0, bytes.duplicate().clear().getInt(bytes.limit()));
        }
    }

    @Test void nativeTimeIsBoundInBothStagesAndBothWorldPrograms() throws Exception {
        String v = "uniform int worldTime;\nvoid main() { gl_Position = ftransform() + vec4(float(worldTime)); }";
        String f = "uniform int worldTime;\nvoid main() { gl_FragColor = vec4(float(worldTime) / 24000.0); }";
        for (boolean shadow : new boolean[] {false, true}) {
            var program = NativeTerrainProgram.adaptWithMesh(v, f, List.of(0), shadow, false);
            assertTrue(program.usesWorldMatrices());
            assertFalse(program.needsShadowMatrices());
            for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                assertTrue(source.contains("int oculusNative_worldTime;"));
                assertTrue(source.contains("float(oculusNative_worldTime)"));
            }
        }
    }

    @Test void nativeTimeRejectsWrongTypeDuplicateAndUnsupportedClockUniforms() {
        for (String declaration : List.of("uniform float worldTime;", "uniform int worldTime, worldTime;",
            "uniform int worldTime[2];", "uniform float worldDay;", "uniform int worldDay[2];",
            "uniform int worldDay, worldDay;"))
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                declaration + "\nvoid main() { gl_Position = ftransform(); }",
                "void main() { gl_FragColor = vec4(1); }", List.of(0)));
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "uniform float timeAngle;\nvoid main(){gl_Position=ftransform()*timeAngle;}",
            "void main(){gl_FragColor=vec4(1);}",List.of(0)));
    }

    @Test void nativeDayBindsInBothStagesWithDefaultAndExtendedMeshes() throws Exception {
        String declarations = "uniform int worldTime, worldDay;\n";
        for (boolean shadow : new boolean[] {false, true}) {
            for (boolean extended : new boolean[] {false, true}) {
                var program = NativeTerrainProgram.adaptWithMesh(declarations +
                    "void main() { gl_Position = ftransform() + vec4(float(worldDay + worldTime)); }",
                    declarations + "void main() { gl_FragColor = vec4(float(worldDay + worldTime)); }",
                    List.of(0), shadow, extended);
                assertTrue(program.usesWorldMatrices());
                assertFalse(program.needsShadowMatrices());
                for (String source : List.of(program.vertexSource(), program.fragmentSource())) {
                    assertTrue(source.contains("int oculusNative_worldDay;"));
                    assertTrue(source.contains("float(oculusNative_worldDay + oculusNative_worldTime)"));
                }
            }
        }
    }

    @Test void fragmentOnlyDayRequestsSharedBlockAndRejectsCrossStageTypeMismatch() throws Exception {
        String vertex = "void main() { gl_Position = ftransform(); }";
        String fragment = "uniform int worldDay; void main() { gl_FragColor = vec4(float(worldDay)); }";
        var program = NativeTerrainProgram.adapt(vertex, fragment, List.of(0));
        assertTrue(program.usesWorldMatrices());
        assertTrue(program.vertexSource().contains(NativeWorldBindings.block()));
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "uniform float worldDay; " + vertex, fragment, List.of(0)));
    }

    @Test void dayPayloadKeepsIntegerPrecisionAndZeroesDefaultAnimationSlots() {
        var identity = new Matrix4f();
        // This day cannot be represented exactly by a float.
        long day = 16_777_217L;
        var data = new WorldMatrixData(identity, identity, identity, identity, 0, 0, 0, 854, 480,
            day * 24000L + 23999);
        for (ByteBuffer bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),
            ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            for (int i = 0; i < bytes.capacity(); i++) bytes.put(i, (byte) 0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            data.write(bytes);
            var nativeBytes = bytes.duplicate().order(ByteOrder.nativeOrder());
            assertEquals(23999, nativeBytes.getInt(8 + 540));
            assertEquals(day, nativeBytes.getInt(8 + 544));
            for (int offset = 548; offset < NativeWorldBindings.HELD_ITEM_IDS_OFFSET; offset += 4)
                assertEquals(0, nativeBytes.getInt(8 + offset));
            assertEquals(-1,nativeBytes.getInt(8 + NativeWorldBindings.HELD_ITEM_IDS_OFFSET));
            assertEquals(-1,nativeBytes.getInt(12 + NativeWorldBindings.HELD_ITEM_IDS_OFFSET));
            assertEquals(8, bytes.position());
            assertEquals(ByteOrder.BIG_ENDIAN, bytes.order());
            assertEquals(0x5a5a5a5a, bytes.getInt(0));
            assertEquals(0x5a5a5a5a, bytes.duplicate().clear().getInt(bytes.limit()));
        }
    }
}
