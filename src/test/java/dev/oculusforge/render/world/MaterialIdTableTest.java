package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class MaterialIdTableTest {
    @Test void encodesAllIdBytesAndUnmappedSentinelExactly() {
        int[] values = {-1, 0, 255, 256, 65535};
        var table = new MaterialIdTable(values);
        for (int i = 0; i < values.length; i++) {
            int pixel = table.pixelAbgr(i);
            assertEquals(255, pixel >>> 24);
            assertEquals(values[i], (pixel & 0xFFFFFF) - 1);
            assertEquals(values[i], table.material(i));
            assertEquals(values[i], table.material(-i));
        }
        assertEquals(-1, table.material(Integer.MIN_VALUE));
        assertEquals(-1, table.material(Integer.MAX_VALUE));
        assertEquals(0xFF000000, table.pixelAbgr(255));
        assertEquals(1, table.height());
        assertThrows(IndexOutOfBoundsException.class, () -> table.pixelAbgr(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> table.pixelAbgr(256));
    }

    @Test void tableReplacementDoesNotRequireChangingVertexStateKeys() {
        int encoded = MaterialVertexContext.encode(1, false);
        int[] source = {-1, -1, 101};
        var a = new MaterialIdTable(source);
        source[2] = 201;
        var b = new MaterialIdTable(source);
        assertEquals(101, a.material(encoded));
        assertEquals(201, b.material(encoded));
        assertEquals(201, b.material(-encoded));
    }

    @Test void validatesBoundsAndPadsLastRowWithUnmappedIds() {
        for (int[] invalid : new int[][] {{}, {0}, {-1, -2}, {-1, 65536}})
            assertThrows(IllegalArgumentException.class, () -> new MaterialIdTable(invalid));
        var table = new MaterialIdTable(MaterialIdTable.unmapped(257));
        assertEquals(2, table.height());
        assertEquals(257, table.size());
        assertEquals(0xFF000000, table.pixelAbgr(511));
        assertThrows(IllegalArgumentException.class, () -> new MaterialIdTable(MaterialIdTable.unmapped(MaterialIdTable.MAX_STATES + 1)));
        assertEquals(1024, new MaterialIdTable(MaterialIdTable.unmapped(MaterialIdTable.MAX_STATES)).height());
    }
}
