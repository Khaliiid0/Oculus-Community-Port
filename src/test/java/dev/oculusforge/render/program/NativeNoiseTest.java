package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeNoiseTest {
    @Test void gpuProbePreparesSharedKnownPngForAllThreePasses() throws Exception {
        var root = Path.of(getClass().getClassLoader().getResource("native-noise").toURI());
        var result = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("probe", "Probe", root, ShaderPackDescriptor.Kind.DIRECTORY));
        assertTrue(result.world().variant("shaders").nativeTerrain().usesNoise());
        assertTrue(result.world().variant("shaders").nativeShadow().usesNoise());
        assertEquals("shaders/noise.png", result.worldNoiseImageKey());
        assertNull(result.noise());
        assertEquals(1, result.images().size());
        var image = result.images().get(result.worldNoiseImageKey());
        assertEquals(2, image.width()); assertEquals(2, image.height());
        assertEquals(0xff834311, image.pixelAbgr(0));
        assertEquals(0xff591fd3, image.pixelAbgr(1));
        assertEquals(0xff07ad2b, image.pixelAbgr(2));
        assertEquals(0xff35e565, image.pixelAbgr(3));
    }
    @TempDir Path directory;
    private static final String V = "void main(){gl_Position=ftransform();}";
    private static final String F = "uniform sampler2D noisetex;\nvoid main(){gl_FragColor=texture2D(noisetex,vec2(.5));}";
    private void file(String name, String text) throws Exception {
        var target = directory.resolve("shaders/" + name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text);
    }
    private void pack(String fragment) throws Exception {
        file("gbuffers_terrain.vsh", V); file("gbuffers_terrain.fsh", fragment);
        file("shadow.vsh", V); file("shadow.fsh", fragment);
        file("final.fsh", "void main(){gl_FragColor=vec4(1);}");
    }
    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("native-noise", "Native noise", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }
    @Test void fragmentNoiseBindsInTerrainAndShadowAcrossMeshModes() throws Exception {
        for (boolean shadow : new boolean[] {false,true}) for (boolean extended : new boolean[] {false,true}) {
            var program = NativeTerrainProgram.adaptWithMesh(V, F, List.of(0), shadow, extended);
            assertTrue(program.usesNoise());
            assertTrue(program.fragmentSource().contains("texture(OculusNoise,"));
            assertFalse(program.usesWorldMatrices());
        }
    }
    @Test void wrongTypesArraysDuplicateReservedAndVertexNoiseStayRejected() {
        for (String f : List.of(F.replace("sampler2D", "sampler3D"), F.replace("noisetex;", "noisetex[2];"),
            "uniform sampler2D noisetex;\n" + F, F.replace("noisetex", "OculusNoise"),
            F.replace("noisetex", "colortex0"))) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(V, f, List.of(0)));
        }
        assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "uniform sampler2D noisetex;\n" + V.replace("ftransform()","ftransform()+texture2D(noisetex,vec2(.5))"), F, List.of(0)));
    }
    @Test void nativeOnlyNoisePreparesOneGeneratedImageAtRequestedResolution() throws Exception {
        pack("const int noiseTextureResolution=127;\n" + F);
        var result = prepare();
        assertTrue(result.world().variant("shaders").nativeTerrain().usesNoise());
        assertTrue(result.world().variant("shaders").nativeShadow().usesNoise());
        assertEquals(127, result.noise().resolution());
        assertTrue(result.images().isEmpty());
        assertNull(result.worldNoiseImageKey());
    }
    @Test void nativeAndPostResolutionConflictsAreRejected() throws Exception {
        pack("const int noiseTextureResolution=128;\n" + F);
        file("final.fsh", "const int noiseTextureResolution=256;\n" + F);
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }
    @Test void nativeOnlyPngOverrideLoadsAndSharesWithPostWithoutGeneratedNoise() throws Exception {
        pack(F);
        file("shaders.properties", "texture.noise=noise.png");
        var image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff123456); image.setRGB(1, 0, 0xffabcdef);
        assertTrue(javax.imageio.ImageIO.write(image, "png", directory.resolve("shaders/noise.png").toFile()));
        var first = prepare();
        assertEquals("shaders/noise.png", first.worldNoiseImageKey());
        assertNull(first.noise());
        assertEquals(1, first.images().size());
        assertEquals(0xff563412, first.images().get(first.worldNoiseImageKey()).pixelAbgr(0));
        file("final.fsh", F);
        var shared = prepare();
        assertNull(shared.noise());
        assertEquals(1, shared.images().size());
        assertEquals(shared.worldNoiseImageKey(), shared.programs().getFirst().fragment().samplers().getFirst().imageKey());
    }
    @Test void missingNativeOnlyOverrideIsNotReplacedWithRandomNoise() throws Exception {
        pack(F); file("shaders.properties", "texture.noise=missing.png");
        assertThrows(java.io.IOException.class, this::prepare);
    }
    @Test void unusedNativeNoiseDoesNotRequestAllocation() throws Exception {
        pack("uniform sampler2D noisetex;\nvoid main(){gl_FragColor=vec4(1);}");
        var result = prepare();
        assertFalse(result.world().variant("shaders").nativeTerrain().usesNoise());
        assertNull(result.noise());
    }
    @Test void nativeOnlyInvalidResolutionCannotAllocate() throws Exception {
        pack("const int noiseTextureResolution=2048;\n" + F);
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }
    @Test void nativeAndPostGeneratedNoiseUseOnePreparedImage() throws Exception {
        pack("const int noiseTextureResolution=64;\n" + F);
        file("final.fsh", "const int noiseTextureResolution=64;\n" + F);
        var result = prepare();
        assertEquals(64 * 64 * 4, result.noise().byteSize());
        assertTrue(result.images().isEmpty());
        assertNull(result.worldNoiseImageKey());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.NOISE,
            result.programs().getFirst().fragment().samplers().getFirst().texture());
    }
}
