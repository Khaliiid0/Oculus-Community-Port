package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.GlslPreprocessor;
import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import dev.oculusforge.shaderpack.ShaderSourceLoader;
import dev.oculusforge.shaderpack.VirtualPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CPU-side description of the world programs a pack asks Oculus to provide.
 * This deliberately separates safe pack inspection from GPU compilation.
 */
public record WorldProgramPlan(Map<String, Variant> variants, boolean oldHandLight, ShadowTerrainPolicy shadowTerrain,
                               Map<String, SkyProgramPlan> skyVariants) {
    public WorldProgramPlan(Map<String, Variant> variants, boolean oldHandLight, ShadowTerrainPolicy shadowTerrain) {
        this(variants, oldHandLight, shadowTerrain, Map.of());
    }
    public WorldProgramPlan(Map<String, Variant> variants) { this(variants, true); }
    public WorldProgramPlan(Map<String, Variant> variants, boolean oldHandLight) { this(variants, oldHandLight, ShadowTerrainPolicy.DEFAULT); }
    public static final int MAX_COLOR_ATTACHMENTS = 8;
    public static final int DEFAULT_SHADOW_RESOLUTION = 1024;
    public static final float DEFAULT_SHADOW_DISTANCE = 128.0F;
    static final List<String> TERRAIN_FALLBACKS = List.of(
        "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_textured", "gbuffers_basic"
    );
    private static final Pattern DIRECTIVE = Pattern.compile(
        "/\\*\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:\\s*([^*]*?)\\s*\\*/"
    );
    private static final Pattern SHADOW_RESOLUTION = Pattern.compile(
        "\\b(?:const\\s+int\\s+)?shadowMapResolution\\s*=\\s*(\\d+)"
    );
    private static final Pattern SHADOW_DISTANCE = Pattern.compile(
        "\\b(?:const\\s+float\\s+)?shadowDistance\\s*=\\s*(\\d+(?:\\.\\d+)?)"
    );

    public WorldProgramPlan {
        variants = Map.copyOf(variants);
        skyVariants = Map.copyOf(skyVariants);
        shadowTerrain = java.util.Objects.requireNonNull(shadowTerrain);
    }

    public static WorldProgramPlan inspect(PackFileSystem pack, List<String> roots, ShaderPackLimits limits,
                                           Map<String, String> environment)
        throws IOException, ShaderCompatibilityException {
        return inspect(pack,roots,limits,environment,Map.of());
    }

    static WorldProgramPlan inspect(PackFileSystem pack,List<String> roots,ShaderPackLimits limits,
                                   Map<String,String> environment,Map<String,CustomUniformBindings> bindings)
        throws IOException,ShaderCompatibilityException {
        Map<String, Variant> variants = new LinkedHashMap<>();
        Map<String, SkyProgramPlan> skyVariants = new LinkedHashMap<>();
        var propertiesPath = VirtualPath.parse("shaders/shaders.properties");
        var custom = pack.exists(propertiesPath) ? NativeCustomUniforms.parse(pack.readUtf8(propertiesPath), environment)
            : NativeCustomUniforms.EMPTY;
        for (String root : roots) {
            var boundCustom=bindings.containsKey(root)?custom.withBindings(bindings.get(root)):custom;
            var sky = SkyProgramPlan.inspect(pack, root, limits, environment, boundCustom);
            if (!sky.programs().isEmpty()) skyVariants.put(root, sky);
            String sourceRoot=bindings.isEmpty()?root:sourceRoot(pack,root);
            if(!root.equals(sourceRoot)&&bindings.get(root).slots().equals(bindings.get(sourceRoot).slots())) continue;
            Set<String> programs = discoverPrograms(pack, sourceRoot);
            String terrain = TERRAIN_FALLBACKS.stream()
                .filter(name -> pack.exists(path(sourceRoot, name + ".fsh")))
                .findFirst().orElse("");
            boolean shadow = pack.exists(path(sourceRoot, "shadow.vsh")) && pack.exists(path(sourceRoot, "shadow.fsh"));
            if (terrain.isEmpty() && !shadow) continue;

            String terrainSource = terrain.isEmpty() ? "" : loadPreprocessed(pack, limits, environment, path(sourceRoot, terrain + ".fsh"));
            ColorBufferFormats.Builder formats = new ColorBufferFormats.Builder(ColorBufferFormats.DEFAULT);
            formats.read(terrainSource, root + "/" + terrain);
            List<Integer> targets = terrain.isEmpty() ? List.of(0) : parseTargets(terrainSource, terrain);
            NativeTerrainProgram nativeTerrain = null;
            String nativeTerrainIssue = terrain.isEmpty() ? "No terrain program" : "Missing paired terrain vertex shader";
            if (!terrain.isEmpty() && pack.exists(path(sourceRoot, terrain + ".vsh"))) {
                try {
                    String terrainVertex = loadPreprocessed(pack, limits, environment, path(sourceRoot, terrain + ".vsh"));
                    formats.read(terrainVertex, root + "/" + terrain + ".vsh");
                    nativeTerrain = NativeTerrainProgram.adaptWithCustomUniforms(
                        terrainVertex, terrainSource, targets, false, TerrainMeshMode.EXTENDED, boundCustom);
                    if(nativeTerrain.usesOpaqueWorldDepth())
                        throw new ShaderCompatibilityException("Native depthtex1 is available only during translucent terrain");
                    // Separate solid/cutout/water programs need per-layer bindings and attachments.
                    if (programs.stream().anyMatch(name -> name.startsWith("gbuffers_terrain_") || name.equals("gbuffers_water")))
                        throw new ShaderCompatibilityException("Pack requests distinct terrain layer programs; legacy base selection is inactive; use per-layer selection");
                    nativeTerrainIssue = "";
                } catch (ShaderCompatibilityException unsupported) {
                    nativeTerrain = null;
                    nativeTerrainIssue = unsupported.getMessage();
                }
            }
            LinkedHashSet<Integer> worldTargets = new LinkedHashSet<>(targets);
            for (String program : programs.stream().sorted().toList()) {
                if (!program.startsWith("gbuffers_")) continue;
                VirtualPath fragment = path(sourceRoot, program + ".fsh");
                if (pack.exists(fragment)) {
                    // This is a conservative allocation scan, not compilation. Inspect every
                    // conditional branch after expanding includes so a pack can switch profiles
                    // without forcing an expensive second JCPP pass for every gbuffer program.
                    String expanded = loadExpanded(pack, limits, fragment);
                    worldTargets.addAll(collectTargets(expanded, program));
                    if (!program.equals(terrain) && ColorBufferFormats.hasDeclarations(expanded)) {
                        formats.read(GlslPreprocessor.preprocess(expanded, environment), fragment.toString());
                    }
                }
            }
            String settings = settingsText(pack, sourceRoot, limits, environment, shadow);
            NativeTerrainProgram nativeShadow = null;
            String nativeShadowIssue = "No paired shadow program";
            if (shadow) {
                try {
                    String shadowVertex = loadPreprocessed(pack, limits, environment, path(sourceRoot, "shadow.vsh"));
                    String shadowFragment = loadPreprocessed(pack, limits, environment, path(sourceRoot, "shadow.fsh"));
                    nativeShadow = NativeTerrainProgram.adaptWithCustomUniforms(shadowVertex, shadowFragment,
                        parseTargets(shadowFragment, "shadow"), true, TerrainMeshMode.EXTENDED, boundCustom);
                    if (programs.stream().anyMatch(name -> List.of("shadow_solid", "shadow_cutout", "shadow_water").contains(name)))
                        throw new ShaderCompatibilityException("Pack requests distinct shadow terrain layer programs; native per-layer shadow selection is not implemented");
                    nativeShadowIssue = "";
                } catch (ShaderCompatibilityException unsupported) {
                    nativeShadow = null;
                    nativeShadowIssue = unsupported.getMessage();
                }
            }
            if (nativeTerrain != null && !nativeTerrain.hasShadowProducer(nativeShadow)) {
                nativeTerrain = null;
                nativeTerrainIssue = "Native shadow texture sampling requires a supported native shadow producer for every requested attachment";
            }
            TerrainLayerPlan layers = TerrainLayerPlan.inspect(pack,sourceRoot,limits,environment,boundCustom,formats)
                .requireShadowProducer(nativeShadow);
            worldTargets.addAll(layers.requiredColorBuffers());
            int resolution = boundedInt(firstNumber(SHADOW_RESOLUTION, settings, DEFAULT_SHADOW_RESOLUTION), 256, 4096);
            float distance = boundedFloat(firstFloat(SHADOW_DISTANCE, settings, DEFAULT_SHADOW_DISTANCE), 32.0F, 512.0F);
            variants.put(root, new Variant(terrain, targets, worldTargets, shadow, resolution, distance, programs, formats.build(),
                nativeTerrain, nativeTerrainIssue, nativeShadow, nativeShadowIssue, layers));
        }
        String properties = pack.exists(propertiesPath) ? pack.readUtf8(propertiesPath) : "";
        return new WorldProgramPlan(variants, dev.oculusforge.render.world.HeldLight.legacyMode(properties),
            ShadowTerrainPolicy.parse(properties, environment), skyVariants);
    }

    static String sourceRoot(PackFileSystem pack,String root) {
        if(TERRAIN_FALLBACKS.stream().anyMatch(name->pack.exists(path(root,name+".fsh")))
            ||pack.exists(path(root,"shadow.vsh"))&&pack.exists(path(root,"shadow.fsh"))) return root;
        return "shaders";
    }

    public Variant variant(String root) {
        return variants.getOrDefault(root, variants.get("shaders"));
    }

    public WorldProgramPlan withRequiredColorBuffers(Map<String, Set<Integer>> required) {
        Map<String, Variant> updated = new LinkedHashMap<>(variants);
        required.forEach((root, buffers) -> {
            Variant current = updated.get(root);
            if (current == null) current = updated.get("shaders");
            if (current != null) updated.put(root, current.withColorTargets(buffers));
        });
        return new WorldProgramPlan(updated, oldHandLight, shadowTerrain, skyVariants);
    }

    public WorldProgramPlan withFormats(Map<String, ColorBufferFormats> requested) {
        Map<String, Variant> updated = new LinkedHashMap<>(variants);
        requested.forEach((root, formats) -> {
            Variant current = variant(root);
            if (current != null) updated.put(root, current.withFormats(formats));
        });
        return new WorldProgramPlan(updated, oldHandLight, shadowTerrain, skyVariants);
    }

    private static Set<String> discoverPrograms(PackFileSystem pack, String root) {
        String prefix = root + "/";
        Set<String> programs = new LinkedHashSet<>();
        for (VirtualPath path : pack.files()) {
            String value = path.toString();
            if (!value.startsWith(prefix)) continue;
            String relative = value.substring(prefix.length());
            if (relative.indexOf('/') >= 0 || !(relative.endsWith(".vsh") || relative.endsWith(".fsh"))) continue;
            programs.add(relative.substring(0, relative.length() - 4));
        }
        return Set.copyOf(programs);
    }

    private static String settingsText(PackFileSystem pack, String root, ShaderPackLimits limits,
                                       Map<String, String> environment, boolean shadow) throws IOException {
        StringBuilder settings = new StringBuilder();
        VirtualPath properties = path("shaders", "shaders.properties");
        if (pack.exists(properties)) settings.append(pack.readUtf8(properties)).append('\n');
        if (shadow) {
            settings.append(PostProcessCapabilities.withoutComments(
                loadPreprocessed(pack, limits, environment, path(root, "shadow.vsh")))).append('\n');
            settings.append(PostProcessCapabilities.withoutComments(
                loadPreprocessed(pack, limits, environment, path(root, "shadow.fsh"))));
        }
        return settings.toString();
    }

    static List<Integer> parseTargets(String source, String program) throws ShaderCompatibilityException {
        return parseTargets(source, program, false);
    }

    private static List<Integer> collectTargets(String source, String program) throws ShaderCompatibilityException {
        return parseTargets(source, program, true);
    }

    private static List<Integer> parseTargets(String source, String program, boolean allocationScan)
        throws ShaderCompatibilityException {
        List<Integer> selected = null;
        Matcher matcher = DIRECTIVE.matcher(source);
        while (matcher.find()) {
            List<Integer> parsed = new ArrayList<>();
            String value = matcher.group(2).trim();
            if (matcher.group(1).equals("DRAWBUFFERS")) {
                String compact = value.replaceAll("\\s+", "");
                if (!compact.matches("[0-9a-fA-F]+") || compact.length() > MAX_COLOR_ATTACHMENTS) {
                    throw new ShaderCompatibilityException(program + " requests unsupported DRAWBUFFERS '" + value + "'");
                }
                compact.chars().forEach(ch -> parsed.add(Character.digit(ch, 16)));
            } else {
                if (!value.matches("\\s*(?:[0-9]|1[0-5])(?:\\s*,\\s*(?:[0-9]|1[0-5]))*\\s*")) {
                    throw new ShaderCompatibilityException(program + " requests unsupported RENDERTARGETS '" + value + "'");
                }
                for (String token : value.split(",")) parsed.add(Integer.parseInt(token.trim()));
            }
            if (parsed.size() > MAX_COLOR_ATTACHMENTS) {
                throw new ShaderCompatibilityException(program + " requests more than eight simultaneous color outputs");
            }
            if (new LinkedHashSet<>(parsed).size() != parsed.size()) {
                throw new ShaderCompatibilityException(program + " repeats a render target in " + value);
            }
            if (selected == null || !allocationScan) selected = List.copyOf(parsed);
            else if (!selected.equals(parsed)) {
                LinkedHashSet<Integer> union = new LinkedHashSet<>(selected);
                union.addAll(parsed);
                selected = List.copyOf(union);
            }
        }
        return selected == null ? List.of(0) : selected;
    }

    private static String loadPreprocessed(PackFileSystem pack, ShaderPackLimits limits, Map<String, String> environment,
                                           VirtualPath path) throws IOException {
        return GlslPreprocessor.preprocess(loadExpanded(pack, limits, path), environment);
    }

    private static String loadExpanded(PackFileSystem pack, ShaderPackLimits limits, VirtualPath path) throws IOException {
        return new ShaderSourceLoader(pack, limits).load(path).source();
    }

    private static int firstNumber(Pattern pattern, String source, int fallback) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static float firstFloat(Pattern pattern, String source, float fallback) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : fallback;
    }

    private static int boundedInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float boundedFloat(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static VirtualPath path(String parent, String file) {
        try {
            return VirtualPath.parse(parent + "/" + file);
        } catch (dev.oculusforge.shaderpack.ShaderPackException impossible) {
            throw new IllegalArgumentException("Invalid built-in shader path", impossible);
        }
    }

    public record Variant(String terrainProgram, List<Integer> terrainTargets, Set<Integer> worldColorTargets, boolean shadowProgram,
                          int shadowResolution, float shadowDistance, Set<String> discoveredPrograms, ColorBufferFormats formats,
                          NativeTerrainProgram nativeTerrain, String nativeTerrainIssue,
                          NativeTerrainProgram nativeShadow, String nativeShadowIssue, TerrainLayerPlan terrainLayers) {
        public Variant {
            terrainProgram = terrainProgram == null ? "" : terrainProgram;
            terrainTargets = List.copyOf(terrainTargets);
            worldColorTargets = Set.copyOf(worldColorTargets);
            discoveredPrograms = Set.copyOf(discoveredPrograms);
            formats = java.util.Objects.requireNonNull(formats);
            terrainLayers = java.util.Objects.requireNonNull(terrainLayers);
            if (!worldColorTargets.containsAll(terrainLayers.requiredColorBuffers()))
                throw new IllegalArgumentException("World allocation must include every layer output");
            nativeTerrainIssue = java.util.Objects.requireNonNull(nativeTerrainIssue);
            if ((nativeTerrain == null) == nativeTerrainIssue.isEmpty())
                throw new IllegalArgumentException("Native terrain must have either a program or an explicit unsupported reason");
            nativeShadowIssue = java.util.Objects.requireNonNull(nativeShadowIssue);
            if ((nativeShadow == null) == nativeShadowIssue.isEmpty())
                throw new IllegalArgumentException("Native shadow must have either a program or an explicit unsupported reason");
            if (terrainTargets.isEmpty() || terrainTargets.size() > MAX_COLOR_ATTACHMENTS
                || new LinkedHashSet<>(terrainTargets).size() != terrainTargets.size()) {
                throw new IllegalArgumentException("Terrain needs one to eight distinct color outputs");
            }
            if (!worldColorTargets.containsAll(terrainTargets)) {
                throw new IllegalArgumentException("World allocation must include every terrain output");
            }
            if (terrainTargets.stream().anyMatch(index -> index < 0 || index >= ColorBufferPlan.MAX_BUFFERS)
                || worldColorTargets.stream().anyMatch(index -> index < 0 || index >= ColorBufferPlan.MAX_BUFFERS)) {
                throw new IllegalArgumentException("World target is outside the supported logical-buffer range");
            }
        }

        public int attachmentCount() {
            return worldColorTargets.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        }

        public List<Integer> shadowTargets() {
            return nativeShadow == null ? List.of(0) : nativeShadow.outputTargets();
        }

        public List<NativeTerrainProgram> nativeTerrainPrograms() {
            if(layerRuntimeEligible())
                return terrainLayers.programs().values().stream().map(TerrainLayerPlan.Program::nativeProgram).distinct().toList();
            return nativeTerrain==null?List.of():List.of(nativeTerrain);
        }

        public boolean layerRuntimeEligible() {
            return terrainLayers.nativeProgramsReady() && discoveredPrograms.stream().noneMatch(name ->
                name.startsWith("gbuffers_terrain_") && !Set.of("gbuffers_terrain_solid","gbuffers_terrain_cutout").contains(name));
        }

        public List<NativeTerrainProgram> nativePrograms() {
            var programs=new ArrayList<>(nativeTerrainPrograms());
            if(nativeShadow!=null) programs.add(nativeShadow);
            return programs.stream().distinct().toList();
        }

        public List<NativeTerrainProgram> preparedNativePrograms() {
            var programs=new ArrayList<>(nativePrograms());
            terrainLayers.programs().values().forEach(p->{if(p.nativeProgram()!=null) programs.add(p.nativeProgram());});
            return programs.stream().distinct().toList();
        }

        public int drawBufferCount() {
            return terrainTargets.size();
        }

        public Set<Integer> initialColorBuffers() {
            LinkedHashSet<Integer> buffers = new LinkedHashSet<>(worldColorTargets);
            buffers.remove(0);
            return Set.copyOf(buffers);
        }

        public Variant withColorTargets(Set<Integer> required) {
            LinkedHashSet<Integer> combined = new LinkedHashSet<>(worldColorTargets);
            combined.addAll(required);
            return new Variant(terrainProgram, terrainTargets, combined, shadowProgram,
                shadowResolution, shadowDistance, discoveredPrograms, formats, nativeTerrain, nativeTerrainIssue, nativeShadow, nativeShadowIssue, terrainLayers);
        }

        public Variant withFormats(ColorBufferFormats requested) {
            return new Variant(terrainProgram, terrainTargets, worldColorTargets, shadowProgram,
                shadowResolution, shadowDistance, discoveredPrograms, requested, nativeTerrain, nativeTerrainIssue, nativeShadow, nativeShadowIssue, terrainLayers);
        }
    }
}
