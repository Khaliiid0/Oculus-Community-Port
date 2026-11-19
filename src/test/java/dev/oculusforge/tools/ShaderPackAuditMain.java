package dev.oculusforge.tools;

import dev.oculusforge.render.program.PackProgramCompiler;
import dev.oculusforge.render.program.PreparedFinalProgram;
import dev.oculusforge.render.program.PreparedPackPipeline;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ShaderPackAuditMain {
    private ShaderPackAuditMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Pass one or more shader-pack ZIP or directory paths");
        }

        int rejected = 0;
        Path nativeDump = null;
        boolean auditSky = java.util.Arrays.asList(args).contains("--audit-sky")
            || java.util.Arrays.asList(args).contains("--audit-sky-vertices");
        boolean auditInputs = java.util.Arrays.asList(args).contains("--audit-native-inputs");
        for (String argument : args) {
            if (argument.equals("--audit-sky-vertices") || argument.equals("--audit-sky")) continue;
            if (argument.equals("--audit-native-inputs")) continue;
            if(argument.startsWith("--dump-native=")) {
                nativeDump=Path.of(argument.substring("--dump-native=".length())).toAbsolutePath().normalize();
                Files.createDirectories(nativeDump);
                continue;
            }
            Path path = Path.of(argument).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Shader pack does not exist: " + path);
            }

            ShaderPackDescriptor.Kind kind = Files.isDirectory(path)
                ? ShaderPackDescriptor.Kind.DIRECTORY
                : ShaderPackDescriptor.Kind.ZIP;
            ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                path.getFileName().toString(),
                path.getFileName().toString(),
                path,
                kind
            );
            PreparedPackPipeline pipeline;
            if (auditInputs) new PackProgramCompiler(ShaderPackLimits.DEFAULT).auditNativeInputs(descriptor)
                .forEach((stage, inputs) -> System.out.printf("  NATIVE-INPUTS %s: %s%n", stage, inputs));
            if (auditSky) new PackProgramCompiler(ShaderPackLimits.DEFAULT).auditSkyPrograms(descriptor)
                .forEach((stage, status) -> System.out.printf("  SKY %s: %s%n", stage, status));
            try {
                pipeline = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(descriptor);
            } catch (dev.oculusforge.render.program.ShaderCompatibilityException | java.io.IOException exception) {
                rejected++;
                System.out.printf("UNSUPPORTED %s: %s%n", path, exception.getMessage());
                continue;
            }
            System.out.printf("PREPARED (not GPU-validated) %s: %d program(s), variants=%s%n",
                path, pipeline.programs().size(), pipeline.variants().keySet());
            pipeline.world().skyVariants().forEach((root, sky) -> sky.programs().forEach((stage, plan) ->
                System.out.printf("  SKY-PLAN %s/%s: source=%s %s%n", root, stage, plan.sourceRoot(),
                    plan.prepared() == null ? "UNSUPPORTED: " + plan.issue() : "PAIR CPU PREPARED; runtime disabled")));
            for (var entry : pipeline.variants().entrySet()) {
                System.out.printf("  EYE-LIGHT %s: halfLifeTicks=%s%n", entry.getKey(), entry.getValue().eyeBrightnessHalfLife());
                var world = pipeline.world().variant(entry.getKey());
                if (world != null) System.out.printf("  WORLD %s: terrain=%s%s%n", entry.getKey(),
                    world.layerRuntimeEligible() ? "LAYERED RUNTIME ELIGIBLE (CPU ONLY): " : world.nativeTerrain() == null ? "INTERNAL BRIDGE ONLY: " : "PACK-NATIVE PREPARED: ",
                    world.layerRuntimeEligible() ? "per-layer programs prepared; GPU validation required" : world.nativeTerrain() == null ? world.nativeTerrainIssue() : world.nativeTerrain().fingerprint());
                if (world != null && world.shadowProgram()) System.out.printf("  WORLD %s: shadow=%s%s%n", entry.getKey(),
                    world.nativeShadow() == null ? "INTERNAL BRIDGE ONLY: " : "PACK-NATIVE PREPARED: ",
                    world.nativeShadow() == null ? world.nativeShadowIssue() : world.nativeShadow().fingerprint());
                if (world != null) {
                    for (var layer : dev.oculusforge.render.program.TerrainLayerPlan.Layer.values()) {
                        var program = world.terrainLayers().program(layer);
                        if(nativeDump!=null && program.nativeProgram()!=null) {
                            String base=path.getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_")+"-"
                                +entry.getKey().replaceAll("[^A-Za-z0-9._-]","_")+"-"+layer;
                            Files.writeString(nativeDump.resolve(base+".vsh"),program.nativeProgram().vertexSource(),java.nio.file.StandardOpenOption.CREATE_NEW);
                            Files.writeString(nativeDump.resolve(base+".fsh"),program.nativeProgram().fragmentSource(),java.nio.file.StandardOpenOption.CREATE_NEW);
                        }
                        System.out.printf("  LAYER %s/%s: program=%s targets=%s CPU-ONLY=%s%n",entry.getKey(),layer,
                            program.name(),program.targets(),program.nativeProgram()==null?program.issue():"CPU PREPARED; see runtime eligibility");
                        System.out.printf("  LAYER-BLEND %s/%s: %s%n",entry.getKey(),layer,
                            world.terrainLayers().blending().program(program.name()).signature());
                    }
                    System.out.printf("  LAYER-LAYOUT %s: opaquePassSplitRequired=%s%n",entry.getKey(),
                        world.terrainLayers().requiresOpaquePassSplit());
                    System.out.printf("  LAYER-RUNTIME %s: eligibleWithOpaqueSplit=%s (not GPU-validated)%n",entry.getKey(),
                        world.layerRuntimeEligible());
                }
                System.out.printf("  TARGETS %s: declared=%s, scratch=%s%n", entry.getKey(),
                    new java.util.TreeMap<>(entry.getValue().programs().getFirst().formats().declared()),
                    entry.getValue().buffers().scratchFormats());
                System.out.printf("  MEMORY %s: scratch1080p=%d, borrowed=%s, retained=%s%n", entry.getKey(),
                    entry.getValue().buffers().scratchBytes(1920, 1080),
                    new java.util.TreeMap<>(entry.getValue().buffers().borrowedColorFormats()),
                    new java.util.TreeSet<>(entry.getValue().buffers().retainedColorSlots().keySet()));
            }
            for (PreparedFinalProgram program : pipeline.programs()) {
                System.out.printf(
                    "  %s: vertex=%d chars, fragment=%d chars, samplers=%s, uniforms=%s%n",
                    program.stageName(),
                    program.vertexSource().length(),
                    program.fragment().source().length(),
                    program.fragment().samplers(),
                    program.fragment().uniforms()
                );
            }
        }
        if (rejected > 0) {
            throw new IllegalStateException(rejected + " pack(s) rejected; see UNSUPPORTED diagnostics above");
        }
    }
}
