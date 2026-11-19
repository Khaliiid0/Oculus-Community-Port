package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CustomTextureTest {
    @TempDir Path directory;

    private static byte[] png(int... argb) throws Exception {
        BufferedImage image = new BufferedImage(argb.length, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb.length, 1, argb, 0, argb.length);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        image.flush();
        return bytes.toByteArray();
    }

    private void file(String path, String text) throws Exception {
        Path target = directory.resolve("shaders/" + path);
        Files.createDirectories(target.getParent()); Files.writeString(target, text);
    }

    private void image() throws Exception {
        Path target = directory.resolve("shaders/textures/data.png");
        Files.createDirectories(target.getParent()); Files.write(target, png(0x80402010, 0xFFABCD12));
    }

    private static String sample(String name) {
        return "#version 120\nuniform sampler2D " + name
            + ";\nvoid main() { gl_FragColor = texture2D(" + name + ", vec2(0.5)); }\n";
    }

    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("test", "Test", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void rgbaChannelsAndAlphaArePreserved() throws Exception {
        var data = PackImageData.decode(png(0x80402010, 0xFFABCD12), 1024, false, false);
        assertEquals(2, data.width()); assertEquals(1, data.height()); assertEquals(8, data.byteSize());
        assertEquals(0x80102040, data.pixelAbgr(0)); assertEquals(0xFF12CDAB, data.pixelAbgr(1));
        assertFalse(data.blur()); assertFalse(data.clamp());
    }

    @Test void customNoiseReplacesGenerationRatherThanAliasingScene() throws Exception {
        image(); file("final.fsh", sample("noisetex"));
        file("shaders.properties", "texture.noise=textures/data.png");
        var prepared = prepare();
        assertNull(prepared.noise()); assertEquals(1, prepared.images().size());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CUSTOM, prepared.programs().getFirst().fragment().samplers().getFirst().texture());
        assertTrue(prepared.variants().get("shaders").buffers().steps().getFirst().colorInputs().isEmpty());
    }

    @Test void namedSamplerIsOnlyAcceptedWithAnExplicitBinding() throws Exception {
        image(); file("final.fsh", sample("lookupTexture"));
        assertThrows(ShaderCompatibilityException.class, this::prepare);
        file("shaders.properties", "customTexture.lookupTexture=textures/data.png");
        assertEquals("shaders/textures/data.png", prepare().programs().getFirst().fragment().samplers().getFirst().imageKey());
    }

    @Test void aliasesAndCompositeFinalStageGroupAreResolvedCorrectly() throws Exception {
        image(); file("deferred.fsh", "/* DRAWBUFFERS:0 */\n" + sample("colortex0"));
        file("composite.fsh", "/* DRAWBUFFERS:0 */\n" + sample("gaux2"));
        file("final.fsh", sample("colortex5"));
        file("shaders.properties", "texture.composite.colortex5=textures/data.png");
        var prepared = prepare();
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.COLOR, prepared.programs().get(0).fragment().samplers().getFirst().texture());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CUSTOM, prepared.programs().get(1).fragment().samplers().getFirst().texture());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CUSTOM, prepared.programs().get(2).fragment().samplers().getFirst().texture());
    }

    @Test void deferredOverridesDoNotLeakIntoCompositeOrFinal() throws Exception {
        image(); file("deferred.fsh", "/* DRAWBUFFERS:0 */\n" + sample("colortex0"));
        file("final.fsh", sample("colortex0"));
        file("shaders.properties", "texture.deferred.gcolor=textures/data.png");
        var prepared = prepare();
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CUSTOM, prepared.programs().getFirst().fragment().samplers().getFirst().texture());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.COLOR, prepared.programs().getLast().fragment().samplers().getFirst().texture());
    }

    @Test void onePathIsDecodedOnceAcrossSamplersAndWorlds() throws Exception {
        image(); file("final.fsh", sample("firstImage")); file("world0/final.fsh", sample("secondImage"));
        file("shaders.properties", "customTexture.firstImage=textures/data.png\ncustomTexture.secondImage=textures/data.png");
        assertEquals(1, prepare().images().size());
    }

    @Test void unusedDeclarationsDoNotLoadMissingImages() throws Exception {
        file("final.fsh", "uniform sampler2D unusedImage;\nvoid main() { gl_FragColor=vec4(1.0); }");
        file("shaders.properties", "customTexture.unusedImage=textures/missing.png");
        assertTrue(prepare().images().isEmpty());
    }

    @Test void missingUsedImageIsAnExplicitFailure() throws Exception {
        file("final.fsh", sample("noisetex")); file("shaders.properties", "texture.noise=textures/missing.png");
        assertThrows(java.io.IOException.class, this::prepare);
    }

    @Test void metadataControlsFilteringAndWrapping() throws Exception {
        image(); file("final.fsh", sample("noisetex")); file("shaders.properties", "texture.noise=textures/data.png");
        for (boolean blur : new boolean[]{false, true}) for (boolean clamp : new boolean[]{false, true}) {
            file("textures/data.png.mcmeta", "{\"texture\":{\"blur\":" + blur + ",\"clamp\":" + clamp + "}}");
            var data = prepare().images().get("shaders/textures/data.png");
            assertEquals(blur, data.blur()); assertEquals(clamp, data.clamp());
        }
    }

    @Test void unsupportedOrMistypedMetadataDoesNotSilentlyChangeBehavior() throws Exception {
        image(); file("final.fsh", sample("noisetex")); file("shaders.properties", "texture.noise=textures/data.png");
        for (String metadata : List.of("{\"animation\":{}}", "{\"texture\":{\"blur\":\"true\"}}",
            "{\"texture\":{\"mipmaps\":true}}", "[]", "null", "{\"texture\":null}")) {
            file("textures/data.png.mcmeta", metadata);
            assertThrows(ShaderCompatibilityException.class, this::prepare, metadata);
        }
    }

    @Test void unsafeAndNonPngPathsAreRejectedBeforeRead() throws Exception {
        file("final.fsh", sample("noisetex"));
        for (String path : List.of("../outside.png", "/outside.png", "C:/outside.png", "https://host/file.png",
            "minecraft:textures/test.png", "a.png TEXTURE_3D RGBA8 2 2 2 RGBA UNSIGNED_BYTE", "a.jpg")) {
            file("shaders.properties", "texture.noise=" + path);
            assertThrows(Exception.class, this::prepare, path);
        }
    }

    @Test void unsupportedPostTargetsAndReservedNamesAreRejected() throws Exception {
        file("final.fsh", sample("colortex0"));
        for (String key : List.of("texture.composite.depthtex1",
            "customTexture.gl_bad", "customTexture.noisetex", "customTexture.gcolor", "customTexture.foo.bar")) {
            file("shaders.properties", key + "=textures/data.png");
            assertThrows(ShaderCompatibilityException.class, this::prepare, key);
        }
    }

    @Test void worldAndUnknownTextureGroupsAreNotMistakenForPostOverrides() throws Exception {
        file("final.fsh", sample("colortex0"));
        for (String key : List.of("texture.final.colortex0", "texture.gbuffers.colortex0")) {
            file("shaders.properties", key + "=textures/missing.png");
            assertTrue(prepare().images().isEmpty(), key);
        }
    }

    @Test void duplicatePropertiesAndConflictingAliasesAreRejected() throws Exception {
        file("final.fsh", sample("colortex0"));
        for (String properties : List.of("texture.noise=a.png\ntexture.noise=b.png",
            "texture.composite.gcolor=a.png\ntexture.composite.colortex0=b.png")) {
            file("shaders.properties", properties);
            assertThrows(ShaderCompatibilityException.class, this::prepare);
        }
    }

    @Test void conditionalPropertiesAreSkippedUntilPackOptionsAreEvaluated() throws Exception {
        file("final.fsh", sample("colortex0"));
        file("shaders.properties", "#if SOME_OPTION\ntexture.noise=missing.png\n#endif");
        assertTrue(prepare().images().isEmpty());
    }

    @Test void excessiveDeclarationsAreBounded() throws Exception {
        file("final.fsh", sample("colortex0"));
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 33; i++) properties.append("customTexture.image").append(i).append("=missing.png\n");
        file("shaders.properties", properties.toString());
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void corruptPngChecksumIsRejected() throws Exception {
        byte[] bytes = png(0xFFFFFFFF); bytes[29] ^= 1;
        assertThrows(ShaderCompatibilityException.class, () -> PackImageData.decode(bytes, 1024, false, false));
    }

    @Test void oversizedDimensionsAndAggregateBudgetAreCheckedBeforeDecoding() throws Exception {
        byte[] bytes = png(0xFFFFFFFF);
        ByteBuffer.wrap(bytes).putInt(16, Integer.MAX_VALUE);
        assertThrows(ShaderCompatibilityException.class, () -> PackImageData.decode(bytes, Long.MAX_VALUE, false, false));
        byte[] valid = png(0xFFFFFFFF, 0xFFFFFFFF);
        assertThrows(ShaderCompatibilityException.class, () -> PackImageData.decode(valid, 7, false, false));
        assertEquals(8, PackImageData.decode(valid, 8, false, false).byteSize());
    }

    @Test void unsupportedBitDepthAndTruncationAreRejected() throws Exception {
        byte[] bytes = png(0xFFFFFFFF); bytes[24] = 16;
        assertThrows(ShaderCompatibilityException.class, () -> PackImageData.decode(bytes, 1024, false, false));
        byte[] truncated = Arrays.copyOf(png(0xFFFFFFFF), 33);
        assertThrows(ShaderCompatibilityException.class, () -> PackImageData.decode(truncated, 1024, false, false));
    }

    @Test void animatedPngIsRejectedInsteadOfDisplayingOnlyItsFirstFrame() throws Exception {
        byte[] original = png(0xFFFFFFFF);
        ByteBuffer control = ByteBuffer.allocate(20);
        control.putInt(8).putInt(0x6163544C).putInt(1).putInt(0);
        CRC32 crc = new CRC32(); crc.update(control.array(), 4, 12); control.putInt((int) crc.getValue());
        var combined = new ByteArrayOutputStream();
        combined.write(original, 0, 33); combined.write(control.array()); combined.write(original, 33, original.length - 33);
        assertThrows(ShaderCompatibilityException.class,
            () -> PackImageData.decode(combined.toByteArray(), 1024, false, false));
    }
}
