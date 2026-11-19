package dev.oculusforge.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NormalFixtureTest {
    @Test void generatorCreatesExactNumericInputsAndRefusesOverwriteOrTraversal(@TempDir Path root) throws Exception {
        for(String invalid:java.util.List.of("../outside","oculus-normal-audit-../../outside","oculus-normal-audit-"))
            assertThrows(IllegalArgumentException.class,()->NormalFixtureMain.generate(root,invalid));
        var pack=NormalFixtureMain.generate(root,"oculus-normal-audit-test");
        assertEquals(root.resolve("oculus-normal-audit-test"),pack);
        assertThrows(IllegalArgumentException.class,()->NormalFixtureMain.generate(root,"oculus-normal-audit-test"));
        var image=javax.imageio.ImageIO.read(pack.resolve("assets/minecraft/textures/block/stone_n.png").toFile());
        try {
            assertEquals(16,image.getWidth());assertEquals(16,image.getHeight());
            assertEquals(0x20404040,image.getRGB(0,0));assertEquals(0x60c04080,image.getRGB(15,0));
            assertEquals(0xa040c0c0,image.getRGB(0,15));assertEquals(0xe0c0c0fc,image.getRGB(15,15));
        } finally {image.flush();}
        assertTrue(Files.readString(pack.resolve("pack.mcmeta")).contains("\"min_format\":[97,1]"));
        assertEquals("format=lab-pbr/1.3",Files.readString(pack.resolve("assets/minecraft/optifine/texture.properties")).trim());
    }
}
