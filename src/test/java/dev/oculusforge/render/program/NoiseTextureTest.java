package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NoiseTextureTest {
    @TempDir Path directory;
    private static final String NOISE = """
        #version 120
        varying vec2 texcoord;
        uniform sampler2D noisetex;
        void main() { gl_FragColor = texture2D(noisetex, texcoord); }
        """;

    private void file(String name, String source) throws Exception {
        Path target = directory.resolve("shaders/" + name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source);
    }

    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("noise-test", "Noise test", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void noiseUsesItsOwnBindingAndDefaultResolution() throws Exception {
        file("final.fsh", NOISE);
        var result = prepare();
        assertNotNull(result.noise());
        assertEquals(256, result.noise().resolution());
        assertEquals(256 * 256 * 4, result.noise().byteSize());
        var sampler = result.programs().getFirst().fragment().samplers().getFirst();
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.NOISE, sampler.texture());
        assertEquals(-1, sampler.colorIndex());
        assertTrue(result.variants().get("shaders").buffers().steps().getFirst().colorInputs().isEmpty());
    }

    @Test void unusedNoiseDoesNotAllocateAnImage() throws Exception {
        file("final.fsh", "uniform sampler2D noisetex;\nvoid main() { gl_FragColor = vec4(1.0); }");
        assertNull(prepare().noise());
    }

    @Test void resolutionIsReadAfterPreprocessingIncludingMacrosAndInactiveBranches() throws Exception {
        file("final.fsh", """
            #define NOISE_SIZE 127
            #if 0
            const int noiseTextureResolution = 4096;
            #endif
            const int noiseTextureResolution = NOISE_SIZE;
            """ + NOISE);
        assertEquals(127, prepare().noise().resolution());
    }

    @Test void matchingResolutionsAcrossStagesAndWorldsShareOneImage() throws Exception {
        String source = "const int noiseTextureResolution = 128;\n" + NOISE;
        file("final.fsh", source);
        file("world0/final.fsh", source);
        file("world-1/final.fsh", source);
        var result = prepare();
        assertEquals(3, result.variants().size());
        assertEquals(128 * 128 * 4, result.noise().byteSize());
    }

    @Test void conflictingStageAndWorldResolutionsAreRejected() throws Exception {
        file("final.fsh", "const int noiseTextureResolution = 128;\n" + NOISE);
        file("world0/final.fsh", "const int noiseTextureResolution = 256;\n" + NOISE);
        var error = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(error.getMessage().contains("Conflicting noiseTextureResolution"));
    }

    @Test void invalidOrAmbiguousResolutionCannotTriggerAnUnboundedAllocation() {
        for (String value : List.of("0", "-1", "1025", "999999999999", "64 * 2", "128.0", "128u")) {
            assertThrows(ShaderCompatibilityException.class,
                () -> NoiseTextureData.mergeResolution(0, "const int noiseTextureResolution = " + value + ";"), value);
        }
        assertThrows(ShaderCompatibilityException.class,
            () -> NoiseTextureData.mergeResolution(0, "const float noiseTextureResolution = 128.;"));
        assertThrows(ShaderCompatibilityException.class, () -> NoiseTextureData.generate(Integer.MAX_VALUE));
    }

    @Test void commentsCannotChangeResolution() throws Exception {
        assertEquals(0, NoiseTextureData.mergeResolution(0,
            "// const int noiseTextureResolution = 4096;\n/* noiseTextureResolution */"));
    }

    @Test void nonPngNoiseImageOverrideRemainsAnExplicitError() throws Exception {
        file("final.fsh", NOISE);
        file("shaders.properties", "texture.noise = textures/noise.jpg\n");
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void pixelsAreDeterministicOpaqueAndHaveIndependentVariedRgbChannels() throws Exception {
        var first = NoiseTextureData.generate(128);
        var second = NoiseTextureData.generate(128);
        long[] sums = new long[3];
        int differentChannels = 0;
        for (int i = 0; i < 128 * 128; i++) {
            int pixel = first.pixelAbgr(i);
            assertEquals(pixel, second.pixelAbgr(i));
            assertEquals(255, pixel >>> 24);
            int r = pixel & 255, g = (pixel >>> 8) & 255, b = (pixel >>> 16) & 255;
            sums[0] += r; sums[1] += g; sums[2] += b;
            if (r != g && g != b && r != b) differentChannels++;
        }
        for (long sum : sums) assertTrue(sum / (128.0 * 128) > 120 && sum / (128.0 * 128) < 135);
        assertTrue(differentChannels > 15000, "Not grayscale or a repeated solid pixel");
        assertThrows(IndexOutOfBoundsException.class, () -> first.pixelAbgr(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> first.pixelAbgr(128 * 128));
    }

    @Test void allocationBoundsAreInclusive() throws Exception {
        assertEquals(4, NoiseTextureData.generate(1).byteSize());
        assertEquals(4 * 1024 * 1024, NoiseTextureData.generate(1024).byteSize());
    }
}
