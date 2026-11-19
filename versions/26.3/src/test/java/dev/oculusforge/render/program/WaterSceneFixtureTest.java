package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import org.junit.jupiter.api.Test;

class WaterSceneFixtureTest {
    private String resource(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/water-scene/"+name)) {
            assertNotNull(input,name);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private List<String> commands(String name) throws Exception {
        return resource("data/oculus_audit/function/"+name+".mcfunction").lines()
            .map(String::trim).filter(line->!line.isEmpty()&&!line.startsWith("#")).toList();
    }

    @Test void metadataIsAcceptedByTheActualPinnedDataPackCodec() throws Exception {
        var pack=JsonParser.parseString(resource("pack.mcmeta")).getAsJsonObject().getAsJsonObject("pack");
        var range=PackFormat.packCodec(PackType.SERVER_DATA).codec().parse(JsonOps.INSTANCE,pack).getOrThrow();
        assertEquals(PackFormat.of(121,0),range.minInclusive());
        assertEquals(PackFormat.of(121,0),range.maxInclusive());
    }

    @Test void sceneWritesAreDimensionPinnedBoundedAndSeparateFromOriginalScene() throws Exception {
        long blocks=0;
        for(String command:commands("build")) {
            assertTrue(command.startsWith("execute in minecraft:overworld run fill "));
            String[] tokens=command.substring("execute in minecraft:overworld run fill ".length()).split(" ");
            int x0=Integer.parseInt(tokens[0]),y0=Integer.parseInt(tokens[1]),z0=Integer.parseInt(tokens[2]);
            int x1=Integer.parseInt(tokens[3]),y1=Integer.parseInt(tokens[4]),z1=Integer.parseInt(tokens[5]);
            assertTrue(x0>=20&&x1<=32&&y0>=-61&&y1<=-52&&z0>=2&&z1<=18);
            blocks+=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);
        }
        assertEquals(2713,blocks);
        assertTrue(commands("build").contains("execute in minecraft:overworld run fill 21 -60 5 31 -55 17 minecraft:water"));
        assertTrue(commands("build").contains("execute in minecraft:overworld run fill 21 -54 5 31 -54 17 minecraft:air"));
    }

    @Test void cameraCommandsAreFixedPlayerOnlyAndDoNotEditWorldData() throws Exception {
        for(String view:List.of("above","under","under_up","restore","nether","end"))
            for(String command:commands(view))
                assertTrue(command.equals("time set day")||command.equals("weather clear")
                    ||command.matches("execute in minecraft:(overworld|the_nether|the_end) run tp @s [-0-9. ]+"),command);
        assertTrue(commands("restore").contains("execute in minecraft:overworld run tp @s 8.483676 -60 2.422331 0 15"));
        for(String command:commands("pad"))assertTrue(command.startsWith("execute unless dimension minecraft:overworld run "));
        assertNull(getClass().getResource("/water-scene/data/minecraft/tags/function/load.json"));
        assertNull(getClass().getResource("/water-scene/data/minecraft/tags/function/tick.json"));
    }
}
