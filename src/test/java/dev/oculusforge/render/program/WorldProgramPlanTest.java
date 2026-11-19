package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oculusforge.shaderpack.DirectoryPackFileSystem;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorldProgramPlanTest {
    @TempDir Path directory;

    @Test void drawBufferLocationsMapToLogicalTargetsInOrder() throws Exception {
        assertEquals(List.of(0, 6), WorldProgramPlan.parseTargets("/* DRAWBUFFERS:06 */", "terrain"));
        assertEquals(List.of(0, 10, 15), WorldProgramPlan.parseTargets("/* DRAWBUFFERS:0aF */", "terrain"));
        assertEquals(List.of(0, 15, 4), WorldProgramPlan.parseTargets("/* RENDERTARGETS:0, 15, 4 */", "terrain"));
    }

    @Test void invalidOrOversizedWorldMrtLayoutsFailClosed() {
        assertThrows(ShaderCompatibilityException.class,
            () -> WorldProgramPlan.parseTargets("/* DRAWBUFFERS:001 */", "terrain"));
        assertThrows(ShaderCompatibilityException.class,
            () -> WorldProgramPlan.parseTargets("/* DRAWBUFFERS:012345678 */", "terrain"));
        assertThrows(ShaderCompatibilityException.class,
            () -> WorldProgramPlan.parseTargets("/* RENDERTARGETS:16 */", "terrain"));
    }

    @Test void inspectionUsesTheActiveTerrainLayoutAndConservativelyAllocatesConditionalBuffers() throws Exception {
        Path root = Files.createDirectories(directory.resolve("shaders/world0"));
        Files.writeString(root.resolve("gbuffers_terrain.fsh"), """
            #version 120
            #if 0
            /* DRAWBUFFERS:1 */
            #else
            /* DRAWBUFFERS:06 */
            #endif
            void main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(1.0); }
            """);
        Files.writeString(root.resolve("gbuffers_water.fsh"), """
            #version 120
            #if WATER_PROFILE
            /* DRAWBUFFERS:7 */
            #else
            /* DRAWBUFFERS:c */
            #endif
            void main() { gl_FragColor = vec4(1.0); }
            """);

        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            var variant = WorldProgramPlan.inspect(pack, List.of("shaders/world0"), ShaderPackLimits.DEFAULT, Map.of())
                .variants().get("shaders/world0");
            assertEquals(List.of(0, 6), variant.terrainTargets());
            assertTrue(variant.worldColorTargets().containsAll(Set.of(0, 1, 6, 7, 12)));
        }
    }

    @Test void shadowSettingsUseActiveCodeNotCommentsOrDisabledBranches() throws Exception {
        Path shaders = Files.createDirectories(directory.resolve("shaders"));
        Files.writeString(shaders.resolve("shadow.vsh"), """
            #version 120
            // const int shadowMapResolution = 4096;
            #if 0
            const int shadowMapResolution = 2048;
            const float shadowDistance = 512.0;
            #else
            const int shadowMapResolution = 512;
            const float shadowDistance = 64.0;
            #endif
            void main() { gl_Position = ftransform(); }
            """);
        Files.writeString(shaders.resolve("shadow.fsh"), "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }");
        try (var pack = new DirectoryPackFileSystem(directory, ShaderPackLimits.DEFAULT)) {
            var variant = WorldProgramPlan.inspect(pack, List.of("shaders"), ShaderPackLimits.DEFAULT, Map.of()).variant("shaders");
            assertEquals(512, variant.shadowResolution());
            assertEquals(64.0F, variant.shadowDistance());
        }
    }
}
