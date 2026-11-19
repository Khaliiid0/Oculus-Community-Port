package dev.oculusforge.render.world;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.oculusforge.port.PreparedShaderSource;
import dev.oculusforge.port.PipelineGeneration;
import dev.oculusforge.port.OwnedPipeline;
import dev.oculusforge.port.ResourceDisposal;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.ShaderCompatibilityException;
import dev.oculusforge.render.program.WorldProgramPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * First native world-renderer generation. It emits a stable terrain gbuffer from
 * vanilla chunk meshes while pack-native shader transformation is developed.
 */
public final class WorldRenderPipeline implements AutoCloseable {
    private static final String TERRAIN_VERTEX = "#version 330 core\n" + dev.oculusforge.port.TerrainShaderAbi.GLOBALS
        + dev.oculusforge.port.TerrainShaderAbi.TERRAIN + dev.oculusforge.port.TerrainShaderAbi.SECTION + """
        layout(std140) uniform Projection { mat4 ProjMat; };
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;
        out vec4 oculusVertexColor;
        out vec2 oculusTexCoord;
        out vec2 oculusLightCoord;
        out vec3 oculusViewPosition;
        void main() {
            vec3 pos = Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
            vec4 view = ModelViewMat * vec4(pos, 1.0);
            gl_Position = ProjMat * view;
            oculusVertexColor = Color;
            oculusTexCoord = UV0;
            oculusLightCoord = clamp((vec2(UV2) / 256.0) + vec2(0.5 / 16.0), vec2(0.5 / 16.0), vec2(15.5 / 16.0));
            oculusViewPosition = view.xyz;
        }
        """;
    private static final String TERRAIN_FRAGMENT_HEADER = """
        #version 330 core
        layout(std140) uniform Fog { vec4 FogColor; float FogEnvironmentalStart; float FogEnvironmentalEnd; float FogRenderDistanceStart; float FogRenderDistanceEnd; float FogSkyEnd; float FogCloudsEnd; };
        uniform sampler2D Sampler0;
        uniform sampler2D Sampler2;
        in vec4 oculusVertexColor;
        in vec2 oculusTexCoord;
        in vec2 oculusLightCoord;
        in vec3 oculusViewPosition;
        float fogValue(float distanceValue, float startValue, float endValue) { return clamp((distanceValue - startValue) / max(endValue - startValue, 0.0001), 0.0, 1.0); }
        vec4 oculusLogicalBuffer(int logical, vec4 shaded, vec3 normal, vec3 lightSample) {
            if (logical == 0) return shaded;
            if (logical == 1 || logical == 2 || logical == 4 || logical == 6) return vec4(normal * 0.5 + 0.5, 1.0);
            if (logical == 3) return vec4(0.0, oculusLightCoord.y, 0.0, 1.0);
            if (logical == 7) return vec4(0.04, 0.04, 0.04, 1.0);
            return vec4(lightSample, 1.0);
        }
        """;
    private static final String TERRAIN_FRAGMENT_MAIN = """
        void main() {
            vec4 albedo = texture(Sampler0, oculusTexCoord) * oculusVertexColor;
        #ifdef ALPHA_CUTOUT
            if (albedo.a < ALPHA_CUTOUT) discard;
        #endif
            vec3 normal = normalize(cross(dFdx(oculusViewPosition), dFdy(oculusViewPosition)));
            vec3 lightSample = texture(Sampler2, oculusLightCoord).rgb;
            float fog = max(fogValue(length(oculusViewPosition), FogEnvironmentalStart, FogEnvironmentalEnd), fogValue(length(oculusViewPosition.xz), FogRenderDistanceStart, FogRenderDistanceEnd));
            vec4 shaded = vec4(albedo.rgb * lightSample, albedo.a);
            shaded = vec4(mix(shaded.rgb, FogColor.rgb, fog * FogColor.a), shaded.a);
        %s
        }
        """;
    private static final String SHADOW_VERTEX = "#version 330 core\n" + dev.oculusforge.port.TerrainShaderAbi.GLOBALS
        + dev.oculusforge.port.TerrainShaderAbi.TERRAIN + dev.oculusforge.port.TerrainShaderAbi.SECTION + """
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        out vec2 oculusTexCoord;
        void main() {
            vec3 pos = Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset;
            gl_Position = ModelViewMat * vec4(pos, 1.0);
        %s
            oculusTexCoord = UV0;
        }
        """.formatted(dev.oculusforge.render.program.ShadowClipSpace.CONVERSION);
    private static final String SHADOW_FRAGMENT = """
        #version 330 core
        uniform sampler2D Sampler0;
        in vec2 oculusTexCoord;
        layout(location=0) out vec4 oculusShadowColor;
        void main() {
        #ifdef ALPHA_CUTOUT
            if (texture(Sampler0, oculusTexCoord).a < ALPHA_CUTOUT) discard;
        #endif
            float depth = 1.0 - gl_FragCoord.z;
            oculusShadowColor = vec4(depth, depth, depth, 1.0);
        }
        """;

    private final Map<String, Variant> variants;
    private final Map<String, Variant> indirectVariants;
    private final PipelineGeneration<RenderPipeline> compiled;
    private boolean closed;

    public CompiledRenderPipeline compiled(RenderPipeline descriptor) { return compiled.get(descriptor); }
    // The same pack plan is shared by separate/indirect descriptor variants.
    // Register the requirement by plan, never by a single backend's pipelines.
    private final java.util.Set<WorldProgramPlan.Variant> postOpaqueDepth = new java.util.HashSet<>();

    public void requirePostOpaqueDepth(String root) {
        postOpaqueDepth.add(java.util.Objects.requireNonNull(variant(root), "Missing opaque-depth world variant").plan());
    }

    public boolean needsOpaqueWorldDepth(Variant variant) {
        return variant.usesOpaqueWorldDepth() || postOpaqueDepth.contains(variant.plan());
    }
    // Borrowed from PackShaderPipeline; never allocated per draw or closed here.
    private final dev.oculusforge.render.program.PackGpuTexture noise;
    private dev.oculusforge.render.program.PackGpuTexture materials;
    private NativeMaterialAtlas specular;
    private NativeMaterialAtlas normals;
    private WorldMatrixBuffer matrices;
    private dev.oculusforge.render.program.BlockMaterialMap materialMap;
    private dev.oculusforge.render.program.ItemMaterialMap itemMap = dev.oculusforge.render.program.ItemMaterialMap.EMPTY;
    private Map<net.minecraft.world.level.block.Block, dev.oculusforge.render.program.BlockMaterialMap.RenderLayer> layerOverrides = Map.of();
    public Map<net.minecraft.world.level.block.Block, dev.oculusforge.render.program.BlockMaterialMap.RenderLayer> layerOverrides() { return layerOverrides; }
    private java.lang.ref.WeakReference<Object> materialWorld = new java.lang.ref.WeakReference<>(null);
    private WorldRenderTargets targets;
    private java.lang.ref.WeakReference<Object> targetWorld = new java.lang.ref.WeakReference<>(null);
    private Variant targetVariant;
    private boolean terrainSubmissionLogged;
    private boolean shadowSubmissionLogged;
    private boolean waitingForCamera;
    private EndSkyPipeline endSky;
    private boolean endSkySubmissionLogged;
    private String lastEndSkyReadiness = "";
    public EndSkyPipeline endSky() { return endSky; }
    public void noteEndSkyReadiness(boolean prepared, boolean deferred, String root) {
        String readiness = root + ":prepared=" + prepared + ",deferred=" + deferred;
        if (!lastEndSkyReadiness.equals(readiness)) {
            OculusForge.LOGGER.info("Oculus End sky readiness: {}", readiness);
            lastEndSkyReadiness = readiness;
        }
    }
    public void noteEndSkySubmission() {
        if (!endSkySubmissionLogged) {
            OculusForge.LOGGER.info("Oculus selected PACK-NATIVE End sky for a live sky draw");
            endSkySubmissionLogged = true;
        }
    }
    private boolean oldHandLight;
    private dev.oculusforge.render.program.ShadowTerrainPolicy shadowTerrain;
    public dev.oculusforge.render.program.ShadowTerrainPolicy shadowTerrain() { return shadowTerrain; }

    private WorldRenderPipeline(Map<String, Variant> variants, Map<String, Variant> indirectVariants,
                                dev.oculusforge.render.program.PackGpuTexture noise,
                                PipelineGeneration<RenderPipeline> compiled) {
        this.compiled = compiled;
        this.variants = Map.copyOf(variants);
        this.indirectVariants = Map.copyOf(indirectVariants);
        this.noise = noise;
    }

    public static WorldRenderPipeline compile(WorldProgramPlan plan,
        dev.oculusforge.render.program.PipelineDescriptorCache<RenderPipeline> descriptors,
        dev.oculusforge.render.program.BlockMaterialMap materialMap,
        dev.oculusforge.render.program.PackGpuTexture noise) throws ShaderCompatibilityException {
        return compile(plan,descriptors,materialMap,noise,dev.oculusforge.render.program.ItemMaterialMap.EMPTY);
    }

    public static WorldRenderPipeline compile(WorldProgramPlan plan,
        dev.oculusforge.render.program.PipelineDescriptorCache<RenderPipeline> descriptors,
        dev.oculusforge.render.program.BlockMaterialMap materialMap,
        dev.oculusforge.render.program.PackGpuTexture noise,dev.oculusforge.render.program.ItemMaterialMap itemMap)
        throws ShaderCompatibilityException {
        return compile(plan,descriptors,materialMap,noise,itemMap,dev.oculusforge.render.program.MaterialFormatResource.read(
            net.minecraft.client.Minecraft.getInstance().getResourceManager()));
    }
    public static WorldRenderPipeline compile(WorldProgramPlan plan,
        dev.oculusforge.render.program.PipelineDescriptorCache<RenderPipeline> descriptors,
        dev.oculusforge.render.program.BlockMaterialMap materialMap,dev.oculusforge.render.program.PackGpuTexture noise,
        dev.oculusforge.render.program.ItemMaterialMap itemMap,dev.oculusforge.render.program.MaterialFormat materialFormat)
        throws ShaderCompatibilityException {
        RenderSystem.assertOnRenderThread();
        materialFormat.requireMatches(dev.oculusforge.render.program.MaterialFormatResource.read(
            net.minecraft.client.Minecraft.getInstance().getResourceManager()));
        var nativePrograms = new java.util.ArrayList<dev.oculusforge.render.program.NativeTerrainProgram>();
        for (var variant : plan.variants().values()) {
            nativePrograms.addAll(activeTerrainPrograms(variant));
            if (variant.nativeShadow() != null) nativePrograms.add(variant.nativeShadow());
        }
        dev.oculusforge.render.program.TextureBindingCapacity.requireNativeSupported(nativePrograms);
        if (noise == null && plan.variants().values().stream().anyMatch(v ->
            activeTerrainPrograms(v).stream().anyMatch(p -> p.usesNoise()) || (v.nativeShadow() != null && v.nativeShadow().usesNoise())))
            throw new ShaderCompatibilityException("Native noisetex has no prepared texture");
        var compiled = new PipelineGeneration<RenderPipeline>(256);
        WorldRenderPipeline pipeline = null;
        try {
        Map<String, Variant> variants = new LinkedHashMap<>();
        Map<String, Variant> indirectVariants = new LinkedHashMap<>();
        for (var entry : plan.variants().entrySet()) {
            variants.put(entry.getKey(), compileVariant(entry.getKey(), entry.getValue(), descriptors, compiled, false));
            indirectVariants.put(entry.getKey(), compileVariant(entry.getKey(), entry.getValue(), descriptors, compiled, true));
        }
        pipeline = new WorldRenderPipeline(variants, indirectVariants, noise, compiled);
        pipeline.itemMap = java.util.Objects.requireNonNull(itemMap);
        pipeline.oldHandLight = plan.oldHandLight();
        pipeline.shadowTerrain = plan.shadowTerrain();
        if(plan.variants().values().stream().anyMatch(v->activeTerrainPrograms(v).stream().anyMatch(p->p.usesSpecular())
            ||v.nativeShadow()!=null&&v.nativeShadow().usesSpecular()))pipeline.specular=NativeMaterialAtlas.load(
                dev.oculusforge.render.program.StaticMaterialAtlasData.Kind.SPECULAR,materialFormat);
        if(plan.variants().values().stream().anyMatch(v->activeTerrainPrograms(v).stream().anyMatch(p->p.usesNormals())
            ||v.nativeShadow()!=null&&v.nativeShadow().usesNormals()))pipeline.normals=NativeMaterialAtlas.load(
                dev.oculusforge.render.program.StaticMaterialAtlasData.Kind.NORMAL,materialFormat);
        pipeline.layerOverrides = BlockLayerOverrides.resolve(materialMap);
        if (Boolean.getBoolean("oculus.experimental.nativeEndSky")) {
            var sky = plan.skyVariants().get("shaders/world1");
            var requested = sky == null ? null : sky.programs().get("gbuffers_skytextured");
            if (requested != null && dev.oculusforge.render.program.SkyRenderStages.endSkyEligible(requested.prepared())) {
                pipeline.endSky = EndSkyPipeline.compile(requested.prepared(), descriptors);
                OculusForge.LOGGER.info("Oculus compiled PACK-NATIVE End sky (experimental)");
            }
        }
        if (plan.variants().values().stream().anyMatch(v ->
            activeTerrainPrograms(v).stream().anyMatch(p -> p.usesMaterialIds()) || (v.nativeShadow() != null && v.nativeShadow().usesMaterialIds()))) {
            pipeline.materials = dev.oculusforge.render.program.PackGpuTexture.create(BlockMaterialResolver.resolve(materialMap));
            pipeline.materialMap = materialMap;
        }
        if ((pipeline.endSky != null && pipeline.endSky.usesWorldMatrices()) || plan.variants().values().stream().anyMatch(v ->
            activeTerrainPrograms(v).stream().anyMatch(p -> p.usesWorldMatrices()) || (v.nativeShadow() != null && v.nativeShadow().usesWorldMatrices())))
            pipeline.matrices = new WorldMatrixBuffer();
        return pipeline;
        } catch (RuntimeException | Error | ShaderCompatibilityException failure) {
            try { if (pipeline == null) compiled.close(); else pipeline.close(); }
            catch (RuntimeException | Error cleanup) { if (failure != cleanup) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static List<dev.oculusforge.render.program.NativeTerrainProgram> activeTerrainPrograms(WorldProgramPlan.Variant plan) {
        return plan.nativeTerrainPrograms();
    }

    private static Variant compileLayeredVariant(String root, WorldProgramPlan.Variant plan,
        dev.oculusforge.render.program.PipelineDescriptorCache<RenderPipeline> descriptors,
        PipelineGeneration<RenderPipeline> compiled, boolean indirect) throws ShaderCompatibilityException {
        String key=dev.oculusforge.render.program.NativeTerrainProgram.hash(root+'\0'
            +plan.terrainLayers().descriptorSignature(plan.formats())+'\0'
            +(plan.nativeShadow()==null?"internal":plan.nativeShadow().fingerprint())+'\0'+indirect);
        var paths=new java.util.EnumMap<dev.oculusforge.render.program.TerrainLayerPlan.Layer,String>(dev.oculusforge.render.program.TerrainLayerPlan.Layer.class);
        paths.put(dev.oculusforge.render.program.TerrainLayerPlan.Layer.SOLID,key+"/opaque");
        paths.put(dev.oculusforge.render.program.TerrainLayerPlan.Layer.CUTOUT,key+"/cutout");
        paths.put(dev.oculusforge.render.program.TerrainLayerPlan.Layer.TRANSLUCENT,key+"/translucent");
        var requested=new java.util.HashSet<>(paths.values());
        if(plan.shadowProgram()) requested.add(key+"/shadow");
        descriptors.requireCapacity(requested);
        var vertices=new java.util.HashMap<Identifier,String>();
        var fragments=new java.util.HashMap<Identifier,String>();
        var pipelines=new java.util.EnumMap<dev.oculusforge.render.program.TerrainLayerPlan.Layer,RenderPipeline>(dev.oculusforge.render.program.TerrainLayerPlan.Layer.class);
        for(var layer:dev.oculusforge.render.program.TerrainLayerPlan.Layer.values()) {
            var program=plan.terrainLayers().program(layer);
            String path=paths.get(layer);
            Identifier vertex=id(path+"_vertex"),fragment=id(path+"_fragment");
            vertices.put(vertex,program.nativeProgram().vertexSource());
            fragments.put(fragment,program.nativeProgram().fragmentSource());
            Float cutout=switch(layer) {case SOLID->null; case CUTOUT->0.5F; case TRANSLUCENT->0.1F;};
            pipelines.put(layer,descriptors.getOrCreate(path,()->terrainPipeline(path,vertex,fragment,
                plan.formats(),program.targets(),program.nativeProgram(),cutout,
                plan.terrainLayers().blending().program(program.name()),plan.terrainLayers().alphaTests(),program.name(),indirect)));
        }
        RenderPipeline shadow=plan.shadowProgram()?descriptors.getOrCreate(key+"/shadow",
            ()->shadowPipeline(key+"/shadow",key,plan.nativeShadow(),plan.terrainLayers().alphaTests(),indirect)):null;
        if(shadow!=null) {
            vertices.put(id(key+"/shadow_vertex"),plan.nativeShadow()==null?SHADOW_VERTEX:plan.nativeShadow().vertexSource());
            fragments.put(id(key+"/shadow_fragment"),plan.nativeShadow()==null?SHADOW_FRAGMENT:plan.nativeShadow().fragmentSource());
        }
        Map<ShaderType, Map<Identifier, String>> source = Map.of(ShaderType.VERTEX, vertices, ShaderType.FRAGMENT, fragments);
        for(var layer:dev.oculusforge.render.program.TerrainLayerPlan.Layer.values()) {
            validate(pipelines.get(layer),source,root+" "+layer+" native terrain",compiled,indirect);
            var program=plan.terrainLayers().program(layer);
            OculusForge.LOGGER.info("Oculus {} compiled PACK-NATIVE {} terrain: {} targets {} blends {}",root,layer,
                program.name(),program.targets(),pipelines.get(layer).getColorTargetStates().stream()
                    .map(state->state.blendFunction().map(Object::toString).orElse("off")).toList());
        }
        if(shadow!=null) {
            validate(shadow,source,root+" shadow",compiled,indirect);
            OculusForge.LOGGER.info("Oculus {} compiled {} shadow GLSL",root,plan.nativeShadow()==null?"INTERNAL":"PACK-NATIVE");
        }
        return new Variant(plan,pipelines.get(dev.oculusforge.render.program.TerrainLayerPlan.Layer.SOLID),
            pipelines.get(dev.oculusforge.render.program.TerrainLayerPlan.Layer.CUTOUT),
            pipelines.get(dev.oculusforge.render.program.TerrainLayerPlan.Layer.TRANSLUCENT),shadow,pipelines);
    }

    private static Variant compileVariant(String root, WorldProgramPlan.Variant plan,
        dev.oculusforge.render.program.PipelineDescriptorCache<RenderPipeline> descriptors,
        PipelineGeneration<RenderPipeline> compiled, boolean indirect)
        throws ShaderCompatibilityException {
        for(var program:activeTerrainPrograms(plan)) {
            if(program.needsShadowMatrices() && !plan.shadowProgram())
                throw new ShaderCompatibilityException("Native layer shadow matrices require an actual shadow program");
            if(!program.hasShadowProducer(plan.nativeShadow()))
                throw new ShaderCompatibilityException("Native layer shadow textures require a supported native shadow producer");
        }
        if(plan.layerRuntimeEligible()) return compileLayeredVariant(root,plan,descriptors,compiled,indirect);
        if (!plan.shadowProgram() && plan.nativeTerrain() != null && plan.nativeTerrain().needsShadowMatrices())
            throw new ShaderCompatibilityException("Native shadow matrices require an actual shadow program");
        if (plan.nativeTerrain() != null && !plan.nativeTerrain().hasShadowProducer(plan.nativeShadow()))
            throw new ShaderCompatibilityException("Native shadow textures require a supported native shadow producer");
        final String key = dev.oculusforge.render.program.NativeTerrainProgram.hash(root + '\0' + plan.terrainTargets()
            + '\0' + plan.terrainTargets().stream().map(plan.formats()::get).toList()
            + '\0' + (plan.nativeTerrain() == null ? "internal" : plan.nativeTerrain().fingerprint())
            + '\0' + plan.terrainLayers().blending().program(plan.terrainProgram()).signature()
            + '\0' + plan.terrainLayers().alphaTests().signature()
            + '\0' + (plan.nativeShadow() == null ? "internal" : plan.nativeShadow().fingerprint()) + '\0' + indirect);
        Identifier terrainVertex = id(key + "/terrain_vertex");
        Identifier terrainFragment = id(key + "/terrain_fragment");
        String vertexSource = plan.nativeTerrain() == null ? TERRAIN_VERTEX : plan.nativeTerrain().vertexSource();
        String fragmentSource = plan.nativeTerrain() == null ? terrainFragment(plan.terrainTargets()) : plan.nativeTerrain().fragmentSource();
        java.util.Set<String> requested = new java.util.HashSet<>(List.of(key + "/opaque", key + "/cutout", key + "/translucent"));
        if (plan.shadowProgram()) requested.add(key + "/shadow");
        descriptors.requireCapacity(requested);
        RenderPipeline opaque = descriptors.getOrCreate(key + "/opaque", () -> terrainPipeline(key + "/opaque", terrainVertex, terrainFragment, plan, null,indirect));
        RenderPipeline cutout = descriptors.getOrCreate(key + "/cutout", () -> terrainPipeline(key + "/cutout", terrainVertex, terrainFragment, plan, 0.5F,indirect));
        RenderPipeline translucent = descriptors.getOrCreate(key + "/translucent", () -> terrainPipeline(key + "/translucent", terrainVertex, terrainFragment, plan, 0.1F,indirect));
        RenderPipeline shadow = plan.shadowProgram() ? descriptors.getOrCreate(key + "/shadow", () -> shadowPipeline(key + "/shadow", key,
            plan.nativeShadow(),plan.terrainLayers().alphaTests(),indirect)) : null;
        Map<ShaderType, Map<Identifier, String>> source = Map.of(
            ShaderType.VERTEX, Map.of(terrainVertex, vertexSource, id(key + "/shadow_vertex"),
                plan.nativeShadow() == null ? SHADOW_VERTEX : plan.nativeShadow().vertexSource()),
            ShaderType.FRAGMENT, Map.of(terrainFragment, fragmentSource, id(key + "/shadow_fragment"),
                plan.nativeShadow() == null ? SHADOW_FRAGMENT : plan.nativeShadow().fragmentSource()));
        validate(opaque, source, root + " terrain", compiled,indirect);
        validate(cutout, source, root + " cutout terrain", compiled,indirect);
        validate(translucent, source, root + " translucent terrain", compiled,indirect);
        if (shadow != null) validate(shadow, source, root + " shadow", compiled,indirect);
        if (plan.nativeTerrain() == null) OculusForge.LOGGER.warn(
            "Oculus {} uses INTERNAL terrain, NOT the pack's terrain GLSL: {}", root, plan.nativeTerrainIssue());
        else OculusForge.LOGGER.info("Oculus {} compiled PACK-NATIVE terrain GLSL ({})", root, plan.nativeTerrain().fingerprint());
        if (shadow != null) {
            if (plan.nativeShadow() == null) OculusForge.LOGGER.warn("Oculus {} uses INTERNAL shadow, NOT the pack's shadow GLSL: {}", root, plan.nativeShadowIssue());
            else OculusForge.LOGGER.info("Oculus {} compiled PACK-NATIVE shadow GLSL ({})", root, plan.nativeShadow().fingerprint());
        }
        return new Variant(plan, opaque, cutout, translucent, shadow);
    }

    private static RenderPipeline terrainPipeline(String path, Identifier vertex, Identifier fragment,
                                                   WorldProgramPlan.Variant plan, Float alphaCutout, boolean indirect) {
        return terrainPipeline(path,vertex,fragment,plan.formats(),plan.terrainTargets(),plan.nativeTerrain(),alphaCutout,
            plan.nativeTerrain() == null ? dev.oculusforge.render.program.TerrainBlendSettings.Program.INHERIT
                : plan.terrainLayers().blending().program(plan.terrainProgram()),plan.terrainLayers().alphaTests(),plan.terrainProgram(),indirect);
    }

    private static RenderPipeline terrainPipeline(String path, Identifier vertex, Identifier fragment,
        dev.oculusforge.render.program.ColorBufferFormats formats, List<Integer> targets,
        dev.oculusforge.render.program.NativeTerrainProgram nativeProgram, Float alphaCutout,
        dev.oculusforge.render.program.TerrainBlendSettings.Program blending,
        dev.oculusforge.render.program.TerrainAlphaSettings alphaTests, String programName, boolean indirect) {
        var builder = TerrainPipelineLayout.builder(indirect,ExtendedTerrainMesh.format())
            .withLocation(id(path))
            .withVertexShader(vertex)
            .withFragmentShader(fragment);
        for (int index = 0; index < targets.size(); index++) {
            int logical = targets.get(index);
            var colorFormat=logical==0?SceneColorFormat.terrain(formats):formats.get(logical);
            builder.withColorTargetState(index, blending.target(logical, path.endsWith("translucent"), colorFormat));
        }
        (nativeProgram==null?dev.oculusforge.render.program.TerrainAlphaSettings.EMPTY:alphaTests).apply(builder,programName,alphaCutout);
        if (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne())
            builder.withShaderDefine(dev.oculusforge.render.program.NativeFragmentDepth.ZERO_TO_ONE_DEFINE);
        if (nativeProgram != null && nativeProgram.usesShadowColor())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusShadowColor", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesShadowColor1())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusShadowColor1", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesShadowDepth())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusShadowDepth", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesOpaqueShadowDepth())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusOpaqueShadowDepth", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesNoise())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusNoise", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesSpecular())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusSpecular", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesNormals())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusNormals", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesOpaqueWorldDepth())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusOpaqueWorldDepth", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null) for (int input : nativeProgram.colorInputs())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()
                .withUniform(dev.oculusforge.render.program.NativeColorSamplers.canonical(input), com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesMaterialIds())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusMaterialIds", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesWorldMatrices())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()
                .withUniform(dev.oculusforge.render.program.NativeWorldBindings.BLOCK_NAME, com.mojang.renderpearl.api.pipeline.UniformType.UNIFORM_BUFFER).build());
        return builder.build();
    }

    private static RenderPipeline shadowPipeline(String path, String key, dev.oculusforge.render.program.NativeTerrainProgram nativeProgram,
        dev.oculusforge.render.program.TerrainAlphaSettings alphaTests, boolean indirect) {
        var builder = TerrainPipelineLayout.builder(indirect,ExtendedTerrainMesh.format())
            .withLocation(id(path))
            .withVertexShader(id(key + "/shadow_vertex"))
            .withFragmentShader(id(key + "/shadow_fragment"));
        (nativeProgram==null?dev.oculusforge.render.program.TerrainAlphaSettings.EMPTY:alphaTests).apply(builder,"shadow",0.1F);
        int colorCount = nativeProgram == null ? 1 : nativeProgram.outputTargets().size();
        for (int location = 0; location < colorCount; location++)
            builder.withColorTargetState(location, ColorTargetState.DEFAULT);
        if (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne())
            builder.withShaderDefine(dev.oculusforge.render.program.ShadowClipSpace.ZERO_TO_ONE_DEFINE);
        if (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne())
            builder.withShaderDefine(dev.oculusforge.render.program.NativeFragmentDepth.ZERO_TO_ONE_DEFINE);
        if (nativeProgram != null && nativeProgram.usesMaterialIds()) builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusMaterialIds", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesNoise())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusNoise", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesSpecular())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusSpecular", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesNormals())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("OculusNormals", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build());
        if (nativeProgram != null && nativeProgram.usesWorldMatrices())
            builder.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()
                .withUniform(dev.oculusforge.render.program.NativeWorldBindings.BLOCK_NAME, com.mojang.renderpearl.api.pipeline.UniformType.UNIFORM_BUFFER).build());
        return builder.build();
    }

    private static void validate(RenderPipeline pipeline, Map<ShaderType, Map<Identifier, String>> sources,
        String label, PipelineGeneration<RenderPipeline> generation, boolean indirect) throws ShaderCompatibilityException {
        try {
            generation.acquire(pipeline, () -> {
                var vertexId = pipeline.getShaders().get(ShaderType.VERTEX);
                var fragmentId = pipeline.getShaders().get(ShaderType.FRAGMENT);
                var pair = dev.oculusforge.port.ShadercInterfacePair.adapt(
                    dev.oculusforge.port.TerrainShaderAbi.vertex(sources.get(ShaderType.VERTEX).get(vertexId),indirect),
                    sources.get(ShaderType.FRAGMENT).get(fragmentId));
                return OwnedPipeline.compile(PreparedShaderSource.pair(vertexId,pair.vertex(),fragmentId,pair.fragment()),
                    source -> RenderSystem.getDevice().compilePipeline(pipeline,source,net.minecraft.util.Util.backgroundExecutor()));
            });
        } catch (RuntimeException failure) {
            var error = new ShaderCompatibilityException("Shader compiler rejected Oculus " + label + "; see latest.log");
            error.initCause(failure);
            throw error;
        }
    }

    private static String terrainFragment(List<Integer> logicalTargets) {
        StringBuilder declarations = new StringBuilder();
        StringBuilder writes = new StringBuilder();
        for (int location = 0; location < logicalTargets.size(); location++) {
            declarations.append("layout(location=").append(location).append(") out vec4 oculusColor")
                .append(location).append(";\n");
            writes.append("    oculusColor").append(location).append(" = oculusLogicalBuffer(")
                .append(logicalTargets.get(location)).append(", shaded, normal, lightSample);\n");
        }
        return TERRAIN_FRAGMENT_HEADER + declarations + TERRAIN_FRAGMENT_MAIN.formatted(writes);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("oculus", "world/" + path); }

    public Variant variant(String root) { return variants.getOrDefault(root, variants.get("shaders")); }

    public Variant variant(String root, boolean indirect) {
        return indirect ? indirectVariants.getOrDefault(root,indirectVariants.get("shaders")) : variant(root);
    }

    public WorldRenderTargets ensureTargets(int width, int height, Variant variant) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("World renderer is closed");
        int shadowResolution = variant.shadow() == null ? 0 : variant.plan().shadowResolution();
        var auxiliary = variant.plan().initialColorBuffers();
        Object world = java.util.Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().level, "No active client world");
        if (materialMap != null && materialWorld.get() != world) {
            // Forge can remap state IDs on connection. Resolve after world creation,
            // before any terrain/shadow draw; resize alone does not rebuild this table.
            try {
                var replacement = dev.oculusforge.render.program.PackGpuTexture.create(BlockMaterialResolver.resolve(materialMap));
                var previous = materials;
                materials = replacement;
                materialWorld = new java.lang.ref.WeakReference<>(world);
                if (previous != null) previous.close();
            } catch (ShaderCompatibilityException failure) {
                throw new IllegalStateException("Could not refresh material IDs for the active world", failure);
            }
        }
        WorldTargetLayout layout = new WorldTargetLayout(width, height, auxiliary, shadowResolution,
            variant.plan().formats(), needsOpaqueWorldDepth(variant),
            shadowResolution == 0 ? java.util.Set.of() : java.util.Set.copyOf(variant.plan().shadowTargets()),
            SceneColorFormat.requiresCapture(variant.plan().formats()));
        if (targets == null || targetWorld.get() != world || targetVariant != variant
            || !targets.matches(layout)) {
            resetTargets();
            targets = WorldRenderTargets.create(layout);
            targetWorld = new java.lang.ref.WeakReference<>(world);
            targetVariant = variant;
        }
        return targets;
    }

    public WorldRenderTargets targets() {
        if (targets == null) throw new IllegalStateException("World targets have not been allocated for this frame");
        return targets;
    }

    void noteTerrainSubmission(String root, Variant variant) {
        if (terrainSubmissionLogged) return;
        terrainSubmissionLogged = true;
        OculusForge.LOGGER.info(
            "Oculus world renderer submitted {} terrain for {} through {} MRT output(s), logical targets {}",
            variant.nativeLayersActive() ? "PACK-NATIVE LAYERED" : variant.plan().nativeTerrain() == null ? "INTERNAL" : "PACK-NATIVE",
            root, variant.plan().drawBufferCount(), variant.plan().terrainTargets()
        );
    }

    void noteShadowSubmission(String root, Variant variant) {
        if (shadowSubmissionLogged) return;
        shadowSubmissionLogged = true;
        OculusForge.LOGGER.info(
            "Oculus world renderer submitted the {} terrain shadow map for {} at {}x{} (distance {})",
            variant.plan().nativeShadow() == null ? "INTERNAL" : "PACK-NATIVE",
            root, variant.plan().shadowResolution(), variant.plan().shadowResolution(), variant.plan().shadowDistance()
        );
    }

    @Override
    public void close() {
        if (closed || WorldSceneCapture.deferClose(this)) return;
        closed = true;
        var oldSpecular = specular; specular = null;
        var oldMaterials = materials; materials = null;
        var oldMatrices = matrices; matrices = null;
        var oldSky = endSky; endSky = null;
        materialWorld.clear();
        var oldNormals=normals;normals=null;
        ResourceDisposal.closeAll(this::resetTargets, oldNormals, oldSpecular, oldMaterials, oldMatrices, oldSky, compiled);
    }

    public boolean updateMatrices(net.minecraft.client.renderer.state.level.LevelRenderState state,
        org.joml.Matrix4fc shadowProjection, org.joml.Matrix4fc shadowView, int width, int height,
        dev.oculusforge.render.program.EyeBrightnessSmoother eyeBrightness,
        dev.oculusforge.render.program.CustomUniformBindings.State customUniforms,
        dev.oculusforge.render.program.CameraHistory cameraHistory,
        dev.oculusforge.render.program.WetnessSmoother wetness) {
        if(specular!=null)specular.requireCurrent(); // Fail before creating/drawing any world pass, never sample a stale atlas.
        if(normals!=null)normals.requireCurrent();
        if (matrices == null) return true;
        var camera = state.cameraRenderState;
        var relativeEye = ((EyePositionState) camera).oculus$getRelativeEye();
        var environment = ((WorldEnvironmentState) camera).oculus$getEnvironment();
        if (environment == null || relativeEye == null || width <= 0 || height <= 0 || !WorldMatrixData.cameraReady(camera.projectionMatrix, camera.viewRotationMatrix,
            camera.pos.x, camera.pos.y, camera.pos.z)) {
            if (!waitingForCamera) OculusForge.LOGGER.info("Oculus deferring native world rendering until the camera projection and viewport are ready");
            waitingForCamera = true;
            return false;
        }
        var level = java.util.Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().level, "No active client world");
        var animation = dev.oculusforge.render.program.RenderAnimationClock.current();
        var cameraFrame = cameraHistory.update(level,animation,camera.pos.x,camera.pos.y,camera.pos.z);
        var smooth = eyeBrightness.update(level, animation, environment.eyeBrightness(), animation.frameTime());
        var wet = wetness.update(level,animation,environment.rainStrength(),animation.frameTime());
        var customValues=customUniforms.update(level,animation,level.getOverworldClockTime(),
            camera.pos.x,camera.pos.y,camera.pos.z,environment.biomeCode(),environment.blindness(),environment.darknessFactor(),
            environment.rainStrength(),environment.eyeBrightness(),environment.precipitation());
        matrices.update(new WorldMatrixData(camera.projectionMatrix, camera.viewRotationMatrix,
            shadowProjection, shadowView, camera.pos.x, camera.pos.y, camera.pos.z, width, height,
            java.util.Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().level,
                "No active client world").getOverworldClockTime(),
            dev.oculusforge.render.program.RenderAnimationClock.current(), relativeEye,
            HeldLight.capture(net.minecraft.client.Minecraft.getInstance().player, oldHandLight), environment, smooth,customValues,
            state.skyRenderState.endFlashIntensity,
            ShaderViewDistance.blocks(net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState),
            environment.fog(),
            NativeSceneInputs.fromSkyAngle(net.minecraft.client.Minecraft.getInstance().options.gamma().get().floatValue(),
                state.skyRenderState.sunAngle),cameraFrame.previous(),itemMap.resolve(environment.heldItems()),wet.value()));
        if (waitingForCamera) OculusForge.LOGGER.info("Oculus camera projection and viewport ready; resuming native world rendering");
        waitingForCamera = false;
        return true;
    }

    public void bindMatrices(com.mojang.renderpearl.api.commands.RenderPass pass) {
        if (matrices == null) throw new IllegalStateException("No native world matrix buffer");
        matrices.bind(pass);
    }

    public void bindNoise(com.mojang.renderpearl.api.commands.RenderPass pass) {
        if (noise == null) throw new IllegalStateException("No native noise texture");
        pass.setUniform("OculusNoise", noise.view(), noise.sampler());
    }

    public void bindSpecular(com.mojang.renderpearl.api.commands.RenderPass pass) {
        if(specular==null)throw new IllegalStateException("Missing native specular atlas");
        specular.bind(pass);
    }
    public void bindNormals(com.mojang.renderpearl.api.commands.RenderPass pass) {
        if(normals==null)throw new IllegalStateException("Missing native normal atlas");
        normals.bind(pass);
    }

    public void bindMaterials(com.mojang.renderpearl.api.commands.RenderPass pass) {
        if (materials != null) pass.setUniform("OculusMaterialIds", materials.view(), materials.sampler());
    }

    public void resetTargets() {
        RenderSystem.assertOnRenderThread();
        if(WorldSceneCapture.ownsTargets(this))throw new IllegalStateException("Cannot reset world targets while the HDR frame still owns them");
        var retired = targets;
        targets = null;
        targetWorld.clear();
        targetVariant = null;
        if (retired != null) retired.close();
    }

    public record Variant(WorldProgramPlan.Variant plan, RenderPipeline opaque, RenderPipeline cutout,
                          RenderPipeline translucent, RenderPipeline shadow,
                          Map<dev.oculusforge.render.program.TerrainLayerPlan.Layer,RenderPipeline> layerPipelines) {
        public Variant { layerPipelines=Map.copyOf(layerPipelines); }
        public Variant(WorldProgramPlan.Variant plan,RenderPipeline opaque,RenderPipeline cutout,RenderPipeline translucent,RenderPipeline shadow) {
            this(plan,opaque,cutout,translucent,shadow,Map.of());
        }
        public boolean nativeLayersActive() { return !layerPipelines.isEmpty(); }
        private dev.oculusforge.render.program.TerrainLayerPlan.Layer layer(RenderPipeline vanilla) {
            String path=vanilla.getLocation().getPath();
            if(path.contains("translucent")) return dev.oculusforge.render.program.TerrainLayerPlan.Layer.TRANSLUCENT;
            if(path.contains("cutout")) return dev.oculusforge.render.program.TerrainLayerPlan.Layer.CUTOUT;
            return dev.oculusforge.render.program.TerrainLayerPlan.Layer.SOLID;
        }
        public dev.oculusforge.render.program.NativeTerrainProgram nativeTerrain(RenderPipeline vanilla) {
            return nativeLayersActive()?plan.terrainLayers().program(layer(vanilla)).nativeProgram():plan.nativeTerrain();
        }
        public List<Integer> terrainTargets(boolean translucentGroup) {
            return nativeLayersActive()?plan.terrainLayers().program(translucentGroup
                ?dev.oculusforge.render.program.TerrainLayerPlan.Layer.TRANSLUCENT
                :dev.oculusforge.render.program.TerrainLayerPlan.Layer.SOLID).targets():plan.terrainTargets();
        }
        public List<Integer> terrainTargets(RenderPipeline vanilla) {
            return nativeLayersActive()?plan.terrainLayers().program(layer(vanilla)).targets():plan.terrainTargets();
        }
        public boolean usesShadowTextures() { return activeTerrainPrograms(plan).stream().anyMatch(p->p.usesShadowTextures()); }
        public boolean usesOpaqueWorldDepth() { return activeTerrainPrograms(plan).stream().anyMatch(p->p.usesOpaqueWorldDepth()); }
        public RenderPipeline terrain(RenderPipeline vanilla) {
            String path = vanilla.getLocation().getPath();
            if (path.contains("translucent")) return translucent;
            if (path.contains("cutout")) return cutout;
            return opaque;
        }
    }
}
