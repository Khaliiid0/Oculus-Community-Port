package dev.oculusforge.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaterProbePacksTest {
    @Test void diagnosticPathsRejectTraversalAliasesDevicesAndNonportableNames(@TempDir Path root) throws Exception {
        for(String name:new String[]{"../outside","/absolute","C:/outside","shaders/.. /outside","shaders/NUL.png","shaders/file.","shaders\\file"})
            assertThrows(java.io.IOException.class,()->WaterProbePacksMain.target(root,name));
        assertEquals(root.resolve("shaders/noise.png"),WaterProbePacksMain.target(root,"shaders/noise.png"));
    }
    @Test void writerValidatesBeforeCreatingAndNeverOverwrites(@TempDir Path root) throws Exception {
        Path destination=root.resolve("probe");
        assertThrows(java.io.IOException.class,()->WaterProbePacksMain.writeNew(destination,Map.of("../bad",new byte[0])));
        assertFalse(Files.exists(destination));
        WaterProbePacksMain.writeNew(destination,Map.of("shaders/file.glsl",new byte[]{1,2,3}));
        assertThrows(FileAlreadyExistsException.class,()->WaterProbePacksMain.writeNew(destination,Map.of("shaders/file.glsl",new byte[]{4})));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(destination.resolve("shaders/file.glsl")));
    }
    @Test void positionProbeRetainsOriginalMainAndAddsOnlyTheExplicitPositionAndOptionalMaterialOutput() {
        String source="#version 120\n#define VSH\nvoid main(){gl_Position=vec4(1);}";
        String position=WaterProbePacksMain.neutralPosition(source,false);
        assertTrue(position.contains("#define main waterProbePackMain"));
        assertTrue(position.contains("#undef main"));
        assertTrue(position.endsWith("void main(){waterProbePackMain();gl_Position=ftransform();}\n"));
        assertFalse(position.contains("waterProbeId"));
        assertTrue(WaterProbePacksMain.neutralPosition(source,true).contains("waterProbeId=mc_Entity.x;"));
    }
}
