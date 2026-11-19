package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PostProcessRoutingTest {
    @TempDir Path directory;
    private static final String COPY = """
        #version 120
        varying vec2 texcoord;
        uniform sampler2D colortex0;
        void main() { gl_FragColor = texture2D(colortex0, texcoord); }
        """;

    private PreparedFinalProgram program(String stage, int output, String... inputs) throws Exception {
        List<LegacyFinalShaderAdapter.SamplerBinding> bindings = new ArrayList<>();
        for (String name : inputs) {
            var adapted = new LegacyFinalShaderAdapter().adapt(
                "uniform sampler2D " + name + ";\nvoid main() { gl_FragColor = texture2D(" + name + ", vec2(0.5)); }\n");
            bindings.addAll(adapted.samplers());
        }
        return new PreparedFinalProgram(null, stage, "", new LegacyFinalShaderAdapter.AdaptedShader("", bindings, List.of()), output);
    }

    private void file(String name, String content) throws Exception {
        Path target = directory.resolve("shaders/" + name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private PreparedPackPipeline prepare() throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("test", "Test", directory, ShaderPackDescriptor.Kind.DIRECTORY));
    }

    @Test void independentBuffersAndAliasesDoNotCollapseIntoMain() throws Exception {
        var plan = ColorBufferPlan.create(List.of(
            program("composite", 3, "colortex0"),
            program("composite1", 0, "colortex0", "composite"),
            program("final", -1, "colortex0", "colortex3")));
        assertEquals(-1, plan.steps().get(0).colorInputs().get("colortex0"));
        assertEquals(-1, plan.steps().get(1).colorInputs().get("colortex0"));
        assertEquals(plan.steps().get(0).outputSlot(), plan.steps().get(1).colorInputs().get("composite"));
        assertEquals(plan.steps().get(0).outputSlot(), plan.steps().get(2).colorInputs().get("colortex3"));
        assertEquals(plan.steps().get(1).outputSlot(), plan.steps().get(2).colorInputs().get("colortex0"));
        assertEquals(plan.steps().get(2).outputSlot(), plan.presentedSlot());
        for (var step : plan.steps()) {
            assertTrue(step.outputSlot() >= 0, "Intermediate stages must never overwrite main");
            assertFalse(step.colorInputs().containsValue(step.outputSlot()), "No texture feedback loops");
        }
    }

    @Test void longReadModifyWriteChainReusesTwoTargets() throws Exception {
        List<PreparedFinalProgram> programs = new ArrayList<>();
        for (int i = 0; i < 32; i++) programs.add(program("composite" + i, 0, "colortex0"));
        programs.add(program("final", -1, "colortex0"));
        var plan = ColorBufferPlan.create(programs);
        assertEquals(2, plan.scratchCount());
        for (int i = 1; i < plan.steps().size(); i++) {
            assertEquals(plan.steps().get(i - 1).outputSlot(), plan.steps().get(i).colorInputs().get("colortex0"));
        }
    }

    @Test void unproducedAuxiliaryBufferIsRejected() {
        var error = assertThrows(ShaderCompatibilityException.class,
            () -> ColorBufferPlan.create(List.of(program("final", -1, "colortex7"))));
        assertTrue(error.getMessage().contains("before a supported stage writes it"));
    }

    @Test void compilerDoesNotInventWorldBuffersForAPostOnlyPack() throws Exception {
        file("final.fsh", COPY.replace("colortex0", "colortex7"));
        var error = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(error.getMessage().contains("before a supported stage writes it"));
    }

    @Test void textureUnitLimitIsCheckedBeforeGpuCompilation() throws Exception {
        file("gbuffers_terrain.fsh", COPY);
        file("final.fsh", samplers(PackProgramCompiler.MAX_SAMPLERS_PER_PROGRAM));
        assertEquals(16, prepare().programs().getFirst().fragment().samplers().size());
        file("final.fsh", samplers(PackProgramCompiler.MAX_SAMPLERS_PER_PROGRAM + 1));
        var error = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(error.getMessage().contains("17 distinct texture bindings"), error.getMessage());
    }

    @Test void textureUnitLimitIncludesVertexStageBindings() throws Exception {
        file("final.fsh", samplers(16));
        file("final.vsh", """
            #version 120
            uniform sampler2D noisetex;
            void main() { gl_Position = ftransform() + texture2D(noisetex, vec2(0.5)); }
            """);
        var error = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(error.getMessage().contains("17 distinct texture bindings"), error.getMessage());
    }

    @Test void unreachableHelperDoesNotConsumeTheSeventeenthTextureUnit() throws Exception {
        file("gbuffers_terrain.fsh", COPY);
        file("final.fsh", samplers(16) + "\nuniform sampler2D noisetex;\n"
            + "vec4 unusedLibraryHelper(){return texture2D(noisetex,vec2(.5));}\n");
        var pipeline = prepare();
        assertEquals(16, pipeline.programs().getFirst().fragment().samplers().size());
        assertNull(pipeline.noise());
        assertFalse(pipeline.programs().getFirst().fragment().source().contains("unusedLibraryHelper"));
    }

    private static String samplers(int count) {
        StringBuilder source = new StringBuilder("#version 120\n");
        for (int index = 0; index < count; index++) source.append("uniform sampler2D ").append(index < 16 ? "colortex" + index : "noisetex").append(";\n");
        source.append("void main() { gl_FragColor = vec4(0.0)");
        for (int index = 0; index < count; index++) source.append(" + texture2D(").append(index < 16 ? "colortex" + index : "noisetex").append(", vec2(0.5))");
        return source.append("; }\n").toString();
    }

    @Test void noFinalPresentsColortexZeroNotTheLastArbitraryBuffer() throws Exception {
        var plan = ColorBufferPlan.create(List.of(program("composite", 0), program("composite1", 3)));
        assertEquals(plan.steps().get(0).outputSlot(), plan.presentedSlot());
    }

    @Test void singleFinalUsesOneScratchTarget() throws Exception {
        var plan = ColorBufferPlan.create(List.of(program("final", -1, "colortex0")));
        assertEquals(1, plan.scratchCount());
        assertEquals(0, plan.presentedSlot());
    }

    @Test void memoryBudgetRejectsHugeTargetsWithoutIntegerOverflow() throws Exception {
        var plan = ColorBufferPlan.create(List.of(program("composite", 0), program("final", -1, "colortex0")));
        assertDoesNotThrow(() -> plan.checkMemoryBudget(1920, 1080, 4));
        assertThrows(IllegalStateException.class, () -> plan.checkMemoryBudget(8192, 8192, 4));
        assertThrows(ArithmeticException.class, () -> plan.checkMemoryBudget(Integer.MAX_VALUE, Integer.MAX_VALUE, 4));
        assertThrows(IllegalArgumentException.class, () -> plan.checkMemoryBudget(0, 1080, 4));
    }

    @Test void attachmentAliasIndicesAreExact() throws Exception {
        String[] aliases = {"gcolor", "colortex1", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < aliases.length; i++) {
            assertEquals(i, program("final", -1, aliases[i]).fragment().samplers().getFirst().colorIndex());
        }
        assertEquals(15, program("final", -1, "colortex15").fragment().samplers().getFirst().colorIndex());
    }

    @Test void shadowAndDepthAliasesAreClassifiedWithoutInventingGdepthPrecision() throws Exception {
        for (String sampler : List.of("shadowtex0", "watershadow")) {
            assertEquals(LegacyFinalShaderAdapter.TextureSemantic.SHADOW_DEPTH,
                program("final", -1, sampler).fragment().samplers().getFirst().texture(), sampler);
        }
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.SHADOW_COLOR,
            program("final", -1, "shadowcolor0").fragment().samplers().getFirst().texture());
        for (String sampler : List.of("depthtex0", "depthtex1", "depthtex2")) {
            assertEquals(LegacyFinalShaderAdapter.TextureSemantic.DEPTH,
                program("final", -1, sampler).fragment().samplers().getFirst().texture(), sampler);
        }
        assertThrows(ShaderCompatibilityException.class, () -> program("final", -1, "gdepth"));
    }

    @Test void secondShadowColorIsDistinctAndRejectedUntilItHasAProducer() throws Exception {
        var adapted = new LegacyFinalShaderAdapter().adapt("uniform sampler2D shadowcolor0;\n"
            + "uniform sampler2D shadowcolor1;\nvoid main(){gl_FragColor=texture2D(shadowcolor0,vec2(.5))+texture2D(shadowcolor1,vec2(.5));}");
        assertEquals(List.of(0, 1), adapted.samplers().stream().map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).toList());
        file("final.fsh", "uniform sampler2D shadowcolor1;\nvoid main(){gl_FragColor=texture2D(shadowcolor1,vec2(.5));}");
        var error = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(error.getMessage().contains("no supported shadow producer"), error.getMessage());
        file("shadow.vsh", "void main(){gl_Position=ftransform();}");
        file("shadow.fsh", "void main(){gl_FragColor=vec4(.25);}");
        assertThrows(ShaderCompatibilityException.class, this::prepare);
        file("shadow.fsh", "/* DRAWBUFFERS:10 */\nvoid main(){gl_FragData[0]=vec4(.75);gl_FragData[1]=vec4(.25);}");
        assertEquals(List.of(1,0),prepare().world().variant("shaders").shadowTargets());
        file("shadow.fsh", "/* DRAWBUFFERS:1 */\nvoid main(){gl_FragColor=vec4(.75);}");
        assertEquals(List.of(1),prepare().world().variant("shaders").shadowTargets());
        file("final.fsh", "uniform sampler2D shadowcolor0;\nvoid main(){gl_FragColor=texture2D(shadowcolor0,vec2(.5));}");
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void parsesSingleOutputDirectives() throws Exception {
        assertEquals(3, PostProcessCapabilities.outputBuffer("composite", "/* DRAWBUFFERS:3 */"));
        assertEquals(15, PostProcessCapabilities.outputBuffer("deferred", "/* RENDERTARGETS: 15 */"));
        assertEquals(-1, PostProcessCapabilities.outputBuffer("final", COPY));
    }

    @Test void opaquePostDepthDoesNotAliasLiveDepth() throws Exception {
        var shader = new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2D depthtex0;
            uniform sampler2D depthtex1;
            uniform sampler2D gdepthtex;
            void main() { gl_FragColor = texture2D(depthtex0, vec2(.5))
                + texture2D(depthtex1, vec2(.5)) + texture2D(gdepthtex, vec2(.5)); }
            """);
        assertEquals(2, shader.samplers().size());
        assertEquals(java.util.Set.of(0, 1), shader.samplers().stream()
            .map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).collect(java.util.stream.Collectors.toSet()));
        assertTrue(shader.source().contains("depthtex1"));
        assertFalse(shader.source().contains("gdepthtex"));
    }

    @Test void postOpaqueDepthRequiresSnapshotProducer() throws Exception {
        file("final.fsh", "uniform sampler2D depthtex1;\nvoid main(){gl_FragColor=texture2D(depthtex1,vec2(.5));}");
        var failure = assertThrows(ShaderCompatibilityException.class, this::prepare);
        assertTrue(failure.getMessage().contains("snapshot bridge"));
    }

    @Test void postOpaqueDepthWorksWithTerrainEvenWhenTerrainDoesNotSampleDepth() throws Exception {
        file("gbuffers_terrain.vsh", "void main(){gl_Position=ftransform();}");
        file("gbuffers_terrain.fsh", "void main(){gl_FragColor=vec4(1);}");
        file("final.fsh", "uniform sampler2D depthtex1;\nvoid main(){gl_FragColor=texture2D(depthtex1,vec2(.5));}");
        var pack = prepare();
        assertNotNull(pack.world().variant("shaders"));
        assertEquals(1, pack.programs().getFirst().fragment().samplers().getFirst().colorIndex());
    }

    @Test void opaqueAndLiveShadowDepthNeverCanonicalizeToOneSampler() throws Exception {
        var adapted = new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2D shadowtex0;
            uniform sampler2D shadowtex1;
            void main() {
                gl_FragColor = texture2D(shadowtex0, vec2(0.5)) + texture2D(shadowtex1, vec2(0.5));
            }
            """);
        assertEquals(2, adapted.samplers().size());
        assertEquals(0, adapted.samplers().get(0).colorIndex());
        assertEquals(1, adapted.samplers().get(1).colorIndex());
        assertTrue(adapted.source().contains("uniform sampler2D shadowtex0;"));
        assertTrue(adapted.source().contains("uniform sampler2D shadowtex1;"));
    }

    @Test void waterShadowAliasStillSharesTheLiveDepthSampler() throws Exception {
        var adapted = new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2D shadowtex0;
            uniform sampler2D watershadow;
            void main() {
                gl_FragColor = texture2D(shadowtex0, vec2(0.5)) + texture2D(watershadow, vec2(0.5));
            }
            """);
        assertEquals(1, adapted.samplers().size());
        assertEquals(0, adapted.samplers().getFirst().colorIndex());
        assertFalse(adapted.source().contains("uniform sampler2D watershadow;"));
    }

    @Test void directRawShadowReadsConvertLegacyDepthOnceForEitherSnapshot() throws Exception {
        for(String sampler:List.of("shadowtex0","shadowtex1","watershadow")) {
            for(String call:List.of("texture2D("+sampler+",vec2(.5))",
                "texture("+sampler+",vec2(.5),1.0)","textureLod("+sampler+",vec2(.5),0.0)",
                "texelFetch("+sampler+",ivec2(0),0)")) {
                var adapted=new LegacyFinalShaderAdapter().adapt("uniform sampler2D "+sampler
                    +";\nvoid main(){gl_FragColor="+call+";}");
                assertTrue(adapted.source().contains("sampleValue.r = 1.0 - sampleValue.r"));
                String helper=call.startsWith("texelFetch")?"oculus_fetchDepth":
                    call.startsWith("textureLod")?"oculus_textureDepthLod":"oculus_textureDepth";
                assertTrue(adapted.source().replaceAll("\\s+", "").contains("oculus_FragColor0="+helper+"("+sampler+","),adapted.source());
                assertFalse(adapted.source().contains("oculus_reverseDepth(oculus_reverseDepth"));
            }
        }
    }

    @Test void shadowComparisonDoesNotReceiveASecondRawDepthConversion() throws Exception {
        var adapted=new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2DShadow shadowtex1;
            void main(){gl_FragColor=shadow2D(shadowtex1,vec3(.5));}
            """);
        assertTrue(adapted.source().contains("oculus_shadowCompare(shadowtex1,"));
        assertFalse(adapted.source().contains("oculus_textureDepth(shadowtex1,"));
        assertTrue(adapted.source().contains("stored = 1.0 - texture(image, coordinate.xy).r"));
    }

    @Test void multipleImplicitAndOutOfRangeTargetsAreRejected() {
        for (String source : List.of(COPY, "/* DRAWBUFFERS:01 */", "/* RENDERTARGETS:0,1 */",
            "/* RENDERTARGETS:16 */", "/* RENDERTARGETS:-1 */")) {
            assertThrows(ShaderCompatibilityException.class,
                () -> PostProcessCapabilities.outputBuffer("composite", source));
        }
        assertDoesNotThrow(() -> PostProcessCapabilities.outputBuffer("composite",
            "/* DRAWBUFFERS:0 */\n/* RENDERTARGETS:0 */"));
    }

    @Test void modernMrtDeclarationsMustMatchTheSelectedSlots() throws Exception {
        assertEquals(List.of(0, 1), PostProcessCapabilities.outputBuffers("composite",
            "/* DRAWBUFFERS:01 */\nlayout(location=0) out vec4 a; layout(location=1) out vec4 b;"));
        for (String source : List.of("out vec4 a; out vec4 b;", "out vec4 a, b;",
            "out vec4 a[2];", "layout(location=1) out vec4 color;", "layout(location=2-1) out vec4 color;")) {
            assertThrows(ShaderCompatibilityException.class, () -> PostProcessCapabilities.outputBuffer("final", source), source);
        }
    }

    @Test void lastActiveDirectiveWinsWithoutMergingOrReorderingLocations() throws Exception {
        Map<String, List<Integer>> cases = Map.of(
            "/* DRAWBUFFERS:01 */\n/* DRAWBUFFERS:10 */", List.of(1, 0),
            "/* DRAWBUFFERS:06 */\n/* DRAWBUFFERS:064 */", List.of(0, 6, 4),
            "/* DRAWBUFFERS:01234567 */\n/* DRAWBUFFERS:89abcdef */", List.of(8, 9, 10, 11, 12, 13, 14, 15));
        for (var entry : cases.entrySet()) {
            assertEquals(entry.getValue(), PostProcessCapabilities.outputBuffers("composite", entry.getKey()));
            assertEquals(entry.getValue(), WorldProgramPlan.parseTargets(entry.getKey(), "gbuffers_terrain"));
        }
    }

    @Test void commentedOutDirectivesDoNotSelectTargets() {
        assertThrows(ShaderCompatibilityException.class, () -> PostProcessCapabilities.outputBuffer("composite",
            "// /* DRAWBUFFERS:0 */\n" + COPY));
    }

    @Test void mixedModernAndLegacyOutputsAreRejected() {
        assertThrows(ShaderCompatibilityException.class, () -> PostProcessCapabilities.outputBuffer("final",
            "out vec4 color; void main() { gl_FragColor = vec4(1.0); }"));
    }

    @Test void helperOutParametersAreNotMistakenForFragmentOutputs() {
        assertDoesNotThrow(() -> PostProcessCapabilities.outputBuffer("final",
            "layout(location=0) out vec4 color; void helper(out float a, out float b) { a=0.; b=1.; }"));
    }

    @Test void inactiveRenderTargetDirectivesArePreprocessedAway() throws Exception {
        file("composite.fsh", "#if 0\n/* DRAWBUFFERS:01 */\n#endif\n/* DRAWBUFFERS:0 */\n" + COPY);
        assertEquals(0, prepare().programs().getFirst().outputBuffer());
    }

    @Test void bufferMetadataDoesNotChangeRoutingButUnsafeFragmentWritesAreRejected() throws Exception {
        for (String source : List.of("const int colortex0Format = RGBA16F;", "const bool colortex1Clear = false;",
            "const bool gaux1MipmapEnabled = true;")) {
            assertEquals(-1, PostProcessCapabilities.outputBuffer("final", source));
        }
        for (String source : List.of("void main() { discard; }", "void main() { gl_FragDepth = 0.0; }"))
            assertThrows(ShaderCompatibilityException.class, () -> PostProcessCapabilities.outputBuffer("final", source));
    }

    @Test void terrainPackProducesAWorldProgramPlan() throws Exception {
        file("final.fsh", COPY);
        file("world0/gbuffers_terrain.fsh", COPY);
        var world = prepare().world().variants().get("shaders/world0");
        assertNotNull(world);
        assertEquals("gbuffers_terrain", world.terrainProgram());
        assertEquals(List.of(0), world.terrainTargets());
    }

    @Test void computeAndOutOfRangeStagesAreNotSilentlySkipped() throws Exception {
        file("final.fsh", COPY);
        file("composite16.fsh", COPY);
        assertThrows(ShaderCompatibilityException.class, this::prepare);
        // Separate directory for the compute check, with the unsupported stage removed by the temp-dir owner.
        Files.delete(directory.resolve("shaders/composite16.fsh"));
        file("composite.csh", "void main() {}");
        assertThrows(ShaderCompatibilityException.class, this::prepare);
    }

    @Test void worldTextureAndFlipPropertiesDoNotBecomePostTextureBindings() throws Exception {
        file("final.fsh", COPY);
        file("shaders.properties", "texture.gbuffers.colortex3 = custom.png\n");
        assertTrue(prepare().images().isEmpty());
        file("shaders.properties", "flip.composite.colortex0 = false\n");
        assertTrue(prepare().images().isEmpty());
    }

    @Test void allWorldZeroStagesAndConditionalVertexSourcesArePrepared() throws Exception {
        file("world0/composite.fsh", "/* DRAWBUFFERS:3 */\n" + COPY);
        file("world0/composite1.fsh", "/* RENDERTARGETS:0 */\n" + COPY.replace("colortex0", "colortex3"));
        file("world0/final.fsh", COPY);
        file("world0/final.vsh", """
            #version 120
            #if 0
            uniform mat4 unsupportedMatrix;
            #endif
            varying vec2 texcoord;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                gl_Position = ftransform();
            }
            """);
        var result = prepare();
        assertEquals(List.of("composite", "composite1", "final"),
            result.variants().get("shaders/world0").programs().stream().map(PreparedFinalProgram::stageName).toList());
        assertFalse(result.variants().get("shaders/world0").programs().getLast().vertexSource().contains("unsupportedMatrix"));
        assertFalse(result.variants().containsKey("shaders"));
    }

    @Test void variantsArePreparedSeparatelyAndRuntimeSelectionUsesRootFallback() throws Exception {
        file("final.fsh", COPY);
        file("world0/final.fsh", COPY);
        file("world-1/final.fsh", COPY);
        file("world1/final.fsh", COPY);
        assertEquals(4, prepare().variants().size());
        var root = new PackShaderPipeline.Variant(List.of(), new ColorBufferPlan(List.of(), 0, -1));
        var overworld = new PackShaderPipeline.Variant(List.of(), new ColorBufferPlan(List.of(), 1, 0));
        try (var compiled = new PackShaderPipeline("test", Map.of("shaders", root, "shaders/world0", overworld))) {
            assertSame(overworld, compiled.variant("shaders/world0"));
            assertSame(root, compiled.variant("shaders/world-1"));
            assertSame(root, compiled.variant("custom"));
        }
        try (var compiled = new PackShaderPipeline("test", Map.of("shaders/world0", overworld))) {
            assertNull(compiled.variant("shaders/world1"));
        }
    }

    @Test void preprocessingDoesNotAdvertiseInventedIrisOrAmdFeatures() throws Exception {
        file("final.fsh", """
            #version 120
            #if defined(IS_IRIS) || defined(MC_GL_VENDOR_AMD) || defined(MC_NORMAL_MAP)
            uniform sampler2D incorrectlyChosenTexture;
            void main() { gl_FragColor = texture2D(incorrectlyChosenTexture, vec2(0.5)); }
            #else
            void main() { gl_FragColor = vec4(1.0); }
            #endif
            """);
        assertTrue(prepare().programs().getFirst().fragment().samplers().isEmpty());
    }

    @Test void commentedUniformsCannotCreateBindings() throws Exception {
        var result = new LegacyFinalShaderAdapter().adapt("""
            /*
            uniform sampler2D fakeTexture;
            void example() { gl_FragColor = texture2D(fakeTexture, vec2(0.0)); }
            */
            void main() { gl_FragColor = vec4(1.0); }
            """);
        assertTrue(result.samplers().isEmpty());
    }
}
