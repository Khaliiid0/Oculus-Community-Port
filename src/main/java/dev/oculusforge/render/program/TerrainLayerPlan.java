package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.*;
import java.io.IOException;
import java.util.*;

/** CPU-only per-layer preparation. Presence here does not enable runtime layer draws. */
public record TerrainLayerPlan(Map<Layer, Program> programs, TerrainBlendSettings blending) {
    public TerrainLayerPlan(Map<Layer, Program> programs) { this(programs, TerrainBlendSettings.EMPTY); }
    public enum Layer {
        SOLID("gbuffers_terrain_solid"), CUTOUT("gbuffers_terrain_cutout"), TRANSLUCENT("gbuffers_water");
        private final String specific;
        Layer(String specific) { this.specific = specific; }
        public List<String> fallbackChain() {
            return List.of(specific, "gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic");
        }
    }

    public record Program(String name, List<Integer> targets, NativeTerrainProgram nativeProgram, String issue) {
        public Program {
            name = Objects.requireNonNull(name);
            targets = List.copyOf(targets);
            issue = Objects.requireNonNull(issue);
            if ((nativeProgram == null) == issue.isEmpty())
                throw new IllegalArgumentException("Layer must have a native program or an unsupported reason");
            if (targets.isEmpty() || targets.size() > WorldProgramPlan.MAX_COLOR_ATTACHMENTS
                || new HashSet<>(targets).size() != targets.size()
                || targets.stream().anyMatch(i -> i < 0 || i >= ColorBufferPlan.MAX_BUFFERS))
                throw new IllegalArgumentException("Invalid terrain layer output layout");
        }
    }

    public TerrainLayerPlan {
        programs = Map.copyOf(programs);
        blending = Objects.requireNonNull(blending);
        if (!programs.keySet().equals(EnumSet.allOf(Layer.class)))
            throw new IllegalArgumentException("Terrain layer plan must describe all three layers");
    }

    static TerrainLayerPlan inspect(PackFileSystem pack, String root, ShaderPackLimits limits,
        Map<String, String> environment, NativeCustomUniforms custom, ColorBufferFormats.Builder formats)
        throws IOException, ShaderCompatibilityException {
        var layers = new EnumMap<Layer, Program>(Layer.class);
        var prepared = new HashMap<String, Program>();
        for (Layer layer : Layer.values()) {
            String name = layer.fallbackChain().stream().filter(candidate ->
                pack.exists(path(root,candidate+".vsh")) || pack.exists(path(root,candidate+".fsh")))
                .findFirst().orElse("");
            if (name.isEmpty()) {
                layers.put(layer,new Program("",List.of(0),null,"No terrain fallback program for " + layer));
                continue;
            }
            Program program = prepared.get(name);
            if (program == null) {
                program = prepare(pack,root,name,limits,environment,custom,formats);
                prepared.put(name,program);
            }
            layers.put(layer,program);
        }
        for (Layer layer : List.of(Layer.SOLID, Layer.CUTOUT)) {
            Program program=layers.get(layer);
            if(program.nativeProgram()!=null && program.nativeProgram().usesOpaqueWorldDepth())
                layers.put(layer,new Program(program.name(),program.targets(),null,
                    "Native depthtex1 is available only during translucent terrain"));
        }
        var properties = path("shaders", "shaders.properties");
        String source = pack.exists(properties) ? pack.readUtf8(properties) : "";
        var names = new HashSet<String>();
        layers.values().forEach(program -> names.add(program.name()));
        var blending = TerrainBlendSettings.parse(source, environment, root, names);
        for (Layer layer : Layer.values()) {
            var program = layers.get(layer);
            var state = blending.program(program.name());
            dev.oculusforge.render.world.IndexedTerrainBlending.requireSupported(program.targets().stream()
                .map(logical -> state.target(logical, layer == Layer.TRANSLUCENT, ColorBufferFormat.RGBA8))
                .toArray(com.mojang.blaze3d.pipeline.ColorTargetState[]::new));
        }
        return new TerrainLayerPlan(layers, blending);
    }

    private static Program prepare(PackFileSystem pack, String root, String name, ShaderPackLimits limits,
        Map<String, String> environment, NativeCustomUniforms custom, ColorBufferFormats.Builder formats)
        throws IOException, ShaderCompatibilityException {
        VirtualPath vertex = path(root,name+".vsh"), fragment = path(root,name+".fsh");
        if (!pack.exists(vertex) || !pack.exists(fragment))
            return new Program(name,List.of(0),null,"Missing paired vertex/fragment sources for " + name);
        var loader = new ShaderSourceLoader(pack,limits);
        String v = GlslPreprocessor.preprocess(loader.load(vertex).source(),environment);
        String f = GlslPreprocessor.preprocess(loader.load(fragment).source(),environment);
        formats.read(v,vertex.toString());
        formats.read(f,fragment.toString());
        List<Integer> targets = WorldProgramPlan.parseTargets(f,name);
        try {
            return new Program(name,targets,NativeTerrainProgram.adaptWithCustomUniforms(
                v,f,targets,false,TerrainMeshMode.EXTENDED,custom),"");
        } catch (ShaderCompatibilityException unsupported) {
            return new Program(name,targets,null,unsupported.getMessage());
        }
    }

    public Program program(Layer layer) { return programs.get(Objects.requireNonNull(layer)); }

    public Set<Integer> requiredColorBuffers() {
        var result = new HashSet<Integer>();
        programs.values().forEach(program -> result.addAll(program.targets()));
        programs.values().forEach(program -> { if(program.nativeProgram()!=null) result.addAll(program.nativeProgram().colorInputs()); });
        return Set.copyOf(result);
    }

    public boolean requiresOpaquePassSplit() {
        return !program(Layer.SOLID).targets().equals(program(Layer.CUTOUT).targets());
    }

    /** The existing opaque pass can switch shaders only with identical ordered attachments. */
    public boolean runtimeReadyWithoutOpaqueSplit() {
        return !requiresOpaquePassSplit() && nativeProgramsReady();
    }

    public boolean nativeProgramsReady() {
        return programs.values().stream().allMatch(p -> p.nativeProgram()!=null);
    }

    public String descriptorSignature(ColorBufferFormats formats) {
        var signature=new StringBuilder();
        for(Layer layer:Layer.values()) {
            Program program=program(layer);
            signature.append(layer).append(':').append(program.name()).append(':').append(program.targets())
                .append(':').append(program.targets().stream().map(formats::get).toList()).append(':')
                .append(program.nativeProgram()==null?program.issue():program.nativeProgram().fingerprint())
                .append(':').append(blending.program(program.name()).signature()).append('\0');
        }
        return signature.toString();
    }

    /** Keep a prepared consumer unsupported when its native shadow producer is missing. */
    public TerrainLayerPlan requireShadowProducer(NativeTerrainProgram producer) {
        var guarded = new EnumMap<Layer,Program>(Layer.class);
        programs.forEach((layer,program) -> guarded.put(layer,
            program.nativeProgram()!=null && !program.nativeProgram().hasShadowProducer(producer)
                ? new Program(program.name(),program.targets(),null,
                    "Native layer shadow sampling requires a supported native shadow producer for every requested attachment") : program));
        return new TerrainLayerPlan(guarded, blending);
    }

    public TerrainLayerPlan requireShadowProducer(boolean available) {
        if (available) return this;
        var guarded = new EnumMap<Layer,Program>(Layer.class);
        programs.forEach((layer,program) -> guarded.put(layer,
            program.nativeProgram()!=null && program.nativeProgram().usesShadowTextures()
                ? new Program(program.name(),program.targets(),null,
                    "Native layer shadow sampling requires a supported native shadow producer") : program));
        return new TerrainLayerPlan(guarded, blending);
    }

    private static VirtualPath path(String root,String file) {
        try { return VirtualPath.parse(root+"/"+file); }
        catch (ShaderPackException invalid) { throw new IllegalArgumentException("Invalid terrain layer source path",invalid); }
    }
}
