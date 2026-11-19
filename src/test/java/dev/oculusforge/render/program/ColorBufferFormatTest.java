package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.GlslPreprocessor;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import dev.oculusforge.render.world.WorldTargetLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ColorBufferFormatTest {
    @TempDir Path directory;

    private static ColorBufferFormats formats(String source) throws Exception {
        var builder = new ColorBufferFormats.Builder(ColorBufferFormats.DEFAULT);
        builder.read(GlslPreprocessor.preprocess(source, Map.of()), "test");
        return builder.build();
    }

    @Test void realPackBlockCommentTablesAreMetadataButLineCommentedEntriesAreNot() throws Exception {
        var formats = formats("""
            /*
            const int colortex0Format = R11F_G11F_B10F;
            const int colortex1Format = RGB8_SNORM;
            const int gaux4Format = RGBA16F;
            */
            // const int colortex1Format = RGBA32F;
            """);
        assertEquals(ColorBufferFormat.R11F_G11F_B10F, formats.get(0));
        assertEquals(ColorBufferFormat.RGB8_SNORM, formats.get(1));
        assertEquals(ColorBufferFormat.RGBA16F, formats.get(7));
        assertEquals(ColorBufferFormat.RGBA8, formats.get(2));
    }

    @Test void inactiveBranchesCannotChooseTheFormat() throws Exception {
        var formats = formats("""
            #if 0
            const int colortex0Format = RGBA32F;
            #else
            const int colortex0Format = RGB16F;
            #endif
            """);
        assertEquals(ColorBufferFormat.RGB16F, formats.get(0));
    }

    @Test void legacyAliasesAndLastDeclarationHaveExactIndices() throws Exception {
        var formats = formats("const int gaux1Format = R8;\nconst int colortex4Format = R16F;\nconst int compositeFormat = RGB10_A2;");
        assertEquals(ColorBufferFormat.R16F, formats.get(4));
        assertEquals(ColorBufferFormat.RGB10_A2, formats.get(3));
    }

    @Test void unsupportedIntegerAndExpressionFormatsFailClearly() {
        assertThrows(ShaderCompatibilityException.class, () -> formats("const int colortex0Format = RGBA32UI;"));
        assertThrows(ShaderCompatibilityException.class, () -> formats("const int colortex0Format = RGBA16F + 1;"));
    }

    @Test void unusedExtensionDeclarationDoesNotExpandTheSupportedBufferRange() throws Exception {
        assertTrue(formats("const int colortex16Format = RGBA16F;").declared().isEmpty());
    }

    @Test void byteSizesMatchActualStorageIncludingPackedFormats() {
        assertEquals(1, ColorBufferFormat.R8.bytesPerPixel());
        assertEquals(4, ColorBufferFormat.RGB8_SNORM.bytesPerPixel());
        assertEquals(8, ColorBufferFormat.RGB16F.bytesPerPixel());
        assertEquals(8, ColorBufferFormat.RGBA16F.bytesPerPixel());
        assertEquals(16, ColorBufferFormat.RGBA32F.bytesPerPixel());
        assertEquals(4, ColorBufferFormat.R11F_G11F_B10F.bytesPerPixel());
        assertEquals(4, ColorBufferFormat.RGB10_A2.bytesPerPixel());
    }

    @Test void everyAdvertisedFormatHasMinecraftTextureMappings() {
        for (var format : ColorBufferFormat.values()) assertDoesNotThrow(format::validateTextureMapping, format.name());
    }

    @Test void paddedRgbKeepsImplicitAlphaOneAndItsActualMemoryCost() {
        for (var format : List.of(ColorBufferFormat.RGB8, ColorBufferFormat.RGB8_SNORM,
            ColorBufferFormat.RGB16, ColorBufferFormat.RGB16_SNORM, ColorBufferFormat.RGB16F, ColorBufferFormat.RGB32F)) {
            assertEquals(4, format.gpu().componentCount());
            assertEquals(7, format.writeMask());
            assertEquals(1, format.clearColor().w());
        }
        assertEquals(15, ColorBufferFormat.RGBA16F.writeMask());
        assertEquals(0, ColorBufferFormat.RGBA16F.clearColor().w());
    }

    private static PreparedFinalProgram program(int output, ColorBufferFormats formats) {
        return new PreparedFinalProgram(null, "composite", "",
            new LegacyFinalShaderAdapter.AdaptedShader("", List.of(), List.of()), List.of(output), formats);
    }

    @Test void deadScratchSlotsAreReusedOnlyForMatchingFormats() throws Exception {
        var formats = formats("const int colortex1Format = RGBA16F;");
        var plan = ColorBufferPlan.create(List.of(program(0, formats), program(1, formats), program(0, formats)));
        assertEquals(2, plan.scratchCount());
        assertEquals(plan.steps().get(0).outputSlot(), plan.steps().get(2).outputSlot());
        assertNotEquals(plan.steps().get(0).outputSlot(), plan.steps().get(1).outputSlot());
        assertEquals(List.of(ColorBufferFormat.RGBA8, ColorBufferFormat.RGBA16F), plan.scratchFormats());
    }

    @Test void borrowedWorldFormatsAreValidatedWithoutAllocatingCopies() throws Exception {
        var formats = formats("const int colortex7Format = RGBA32F;\nconst int colortex0Format = RGB16F;");
        var shader = new LegacyFinalShaderAdapter().adapt("uniform sampler2D colortex7;\nvoid main() { gl_FragColor=texture2D(colortex7, vec2(0.5)); }");
        var finalProgram = new PreparedFinalProgram(null, "final", "", shader, List.of(-1), formats);
        var plan = ColorBufferPlan.create(List.of(finalProgram), Set.of(7), formats);
        assertEquals(ColorBufferFormat.RGBA32F, plan.borrowedColorFormats().get(7));
        assertEquals(Map.of("colortex7", 7), plan.steps().getFirst().worldColorInputs());
        assertEquals(ColorBufferFormat.RGBA8, plan.scratchFormats().get(plan.presentedSlot()));
        assertEquals(4L * 100 * 100, plan.scratchBytes(100, 100));
    }

    @Test void hdrMemoryBudgetCannotUseTheOldFourByteEstimate() {
        var plan = new ColorBufferPlan(List.of(), 8, -1, Map.of(), java.util.Collections.nCopies(8, ColorBufferFormat.RGBA32F));
        assertThrows(IllegalStateException.class, () -> plan.checkMemoryBudget(1920, 1080));
        assertThrows(ArithmeticException.class, () -> plan.scratchBytes(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test void unusedWorldImportsDoNotConsumeScratchTextures() throws Exception {
        var plan = ColorBufferPlan.create(List.of(program(-1, ColorBufferFormats.DEFAULT)), Set.of(1, 2, 7));
        assertTrue(plan.borrowedColorFormats().isEmpty());
        assertEquals(1, plan.scratchCount());
    }

    @Test void sparseWorldBudgetAndIdentityIncludeFormats() throws Exception {
        var formats = formats("const int colortex7Format = RGBA16F;");
        var layout = new WorldTargetLayout(100, 100, Set.of(7), 0, formats);
        assertEquals(80_000, layout.byteSize());
        assertNotEquals(layout, new WorldTargetLayout(100, 100, Set.of(7), 0));
    }

    @Test void lateFinalDeclarationsAffectEarlierPostAndWorldTargets() throws Exception {
        Path shaders = Files.createDirectories(directory.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), "/* DRAWBUFFERS:07 */\nvoid main() { gl_FragData[0]=vec4(1.0); gl_FragData[1]=vec4(1.0); }");
        Files.writeString(shaders.resolve("composite.fsh"), "/* DRAWBUFFERS:7 */\nvoid main() { gl_FragColor=vec4(1.0); }");
        Files.writeString(shaders.resolve("final.fsh"), """
            /*
            const int colortex7Format = RGBA16F;
            */
            uniform sampler2D colortex7;
            void main() { gl_FragColor=texture2D(colortex7, vec2(0.5)); }
            """);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("formats", "Formats", directory, ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(ColorBufferFormat.RGBA16F, prepared.world().variant("shaders").formats().get(7));
        var variant = prepared.variants().get("shaders");
        assertEquals(ColorBufferFormat.RGBA16F, variant.buffers().scratchFormats().get(variant.buffers().steps().getFirst().outputSlot()));
        assertEquals(ColorBufferFormat.RGBA8, variant.buffers().scratchFormats().get(variant.buffers().presentedSlot()));
    }

    @Test void fixturesExerciseMixedFormatMrtAndHdrPresentation() throws Exception {
        for (String name : List.of("hdr-roundtrip", "hdr-no-final")) {
            var resource = getClass().getClassLoader().getResource(name);
            assertNotNull(resource);
            var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
                name, name, Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
            var variant = prepared.variants().get("shaders");
            assertTrue(variant.buffers().scratchFormats().contains(ColorBufferFormat.RGBA16F));
            assertEquals(name.equals("hdr-no-final") ? ColorBufferFormat.RGBA16F : ColorBufferFormat.RGBA8,
                variant.buffers().scratchFormats().get(variant.buffers().presentedSlot()));
        }
    }

    @Test void looseFormatMetadataCannotLeaveUndefinedGlConstantsInPostShaders() throws Exception {
        var fragment = new LegacyFinalShaderAdapter().adapt("const int colortex7Format = RGBA16F;\nvoid main(){gl_FragColor=vec4(1.0);}");
        var vertex = new LegacyFinalVertexAdapter().adapt("const int gaux4Format = RGBA16F;\nvoid main(){gl_Position=ftransform();}");
        assertFalse(fragment.source().contains("RGBA16F"));
        assertFalse(vertex.source().contains("RGBA16F"));
    }

    @Test void worldBorrowFixtureReadsWorldThenPostVersionWithoutFeedback() throws Exception {
        var resource = getClass().getClassLoader().getResource("world-buffer-borrow");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(new ShaderPackDescriptor(
            "borrow", "Borrow", Path.of(resource.toURI()), ShaderPackDescriptor.Kind.DIRECTORY));
        var plan = prepared.variants().get("shaders").buffers();
        assertEquals(Map.of(7, ColorBufferFormat.RGBA16F, 8, ColorBufferFormat.RGB8_SNORM), plan.borrowedColorFormats());
        assertEquals(Map.of("colortex7", 7, "colortex8", 8), plan.steps().getFirst().worldColorInputs());
        assertEquals(Map.of("colortex8", 8), plan.steps().getLast().worldColorInputs());
        assertEquals(3, plan.scratchCount());
        assertEquals(plan.steps().getFirst().outputSlots().getFirst(), plan.steps().getLast().colorInputs().get("colortex7"));
    }
}
