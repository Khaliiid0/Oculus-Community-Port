package dev.oculusforge.render.program;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BindGroupLayout.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.GlslPreprocessor;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import dev.oculusforge.shaderpack.ShaderSourceLoader;
import dev.oculusforge.shaderpack.VirtualPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import dev.oculusforge.render.world.WorldRenderPipeline;

public final class PackProgramCompiler {
   static final int MAX_SAMPLERS_PER_PROGRAM = TextureBindingCapacity.MAX_BINDINGS;
   static final long MAX_PREPARED_SOURCE_CHARS = 32L * 1024 * 1024;
   private static final List<String> FULLSCREEN_STAGES = fullscreenStages();
   // Do not impersonate Iris or invent the user's GPU vendor/capabilities.
   private static final Map<String, String> GLSL_ENVIRONMENT = environment();

   private static Map<String, String> environment() {
      Map<String, String> macros = new LinkedHashMap<>();
      macros.put("MC_VERSION", "260200");
      macros.put("OCULUS_POST_PROCESSING", "1");
      macros.put("MC_RENDER_QUALITY", "1.0");
      macros.put("MC_HAND_DEPTH", "0.125");
      macros.put("MAX_COLOR_BUFFERS", "16");
      macros.putAll(SkyRenderStages.macros());
      String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
      if (os.startsWith("windows")) macros.put("MC_OS_WINDOWS", "");
      else if (os.startsWith("mac")) macros.put("MC_OS_MAC", "");
      else if (os.contains("linux")) macros.put("MC_OS_LINUX", "");
      return Map.copyOf(macros);
   }

   private final ShaderPackLimits limits;
   private final PipelineDescriptorCache<RenderPipeline> descriptors = new PipelineDescriptorCache<>();
   private final PipelineDescriptorCache<RenderPipeline> worldDescriptors = new PipelineDescriptorCache<>();

   public int cachedPipelineCount() { return descriptors.size() + worldDescriptors.size(); }
   public void resetPipelineDescriptors() { RenderSystem.assertOnRenderThread(); descriptors.clear(); worldDescriptors.clear();
      dev.oculusforge.render.world.ScenePipelineFormats.reset(); }
   private final LegacyFinalShaderAdapter adapter;
   private final LegacyFinalVertexAdapter vertexAdapter;

   /** Read-only declaration audit; reachability is diagnostic, not permission to bind unknown inputs. */
   public Map<String, List<String>> auditNativeInputs(ShaderPackDescriptor descriptor) throws IOException, ShaderCompatibilityException {
      var results = new LinkedHashMap<String, List<String>>();
      try (var pack = descriptor.open(limits)) {
         long total = 0;
         for (String root : PostProcessCapabilities.ROOTS) for (String stage :
             List.of("shadow", "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water")) {
            for (String extension : List.of(".vsh", ".fsh")) {
               var path = VirtualPath.parse(root + "/" + stage + extension);
               if (!pack.exists(path)) continue;
               String source = GlslPreprocessor.preprocess(new ShaderSourceLoader(pack, limits).load(path).source(), GLSL_ENVIRONMENT);
               total = addPreparedSourceChars(total, source.length(), 0);
               source = NativeShaderDeclarations.expandLists(PostProcessCapabilities.withoutComments(source));
               String reachable = GlslFunctionReachability.prune(source);
               var declaration = java.util.regex.Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*([^;{}]*);");
               String references = declaration.matcher(source).replaceAll("$3");
               String liveReferences = declaration.matcher(reachable).replaceAll("$3");
               var uniforms = declaration.matcher(source);
               var inputs = new ArrayList<String>();
               while (uniforms.find()) {
                  String name = uniforms.group(2);
                  if (NativeWorldBindings.supports(name) || extension.equals(".fsh") && uniforms.group(1).startsWith("sampler")) continue;
                  var word = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(name) + "\\b");
                  inputs.add(uniforms.group(1) + " " + name + ": " + (!word.matcher(references).find() ? "UNREFERENCED"
                      : word.matcher(liveReferences).find() ? "REFERENCED" : "UNREACHABLE-HELPER-ONLY"));
               }
               results.put(path.toString(), List.copyOf(inputs));
            }
         }
      }
      return java.util.Collections.unmodifiableMap(results);
   }

   public PackProgramCompiler(ShaderPackLimits limits) {
      this.limits = limits;
      this.adapter = new LegacyFinalShaderAdapter();
      this.vertexAdapter = new LegacyFinalVertexAdapter();
   }

   private Map<String,CustomUniformBindings> prepareCustomBindings(PackFileSystem pack)
       throws IOException,ShaderCompatibilityException {
      var path=VirtualPath.parse("shaders/shaders.properties");
      var custom=pack.exists(path)?NativeCustomUniforms.parse(pack.readUtf8(path),GLSL_ENVIRONMENT):NativeCustomUniforms.EMPTY;
      var result=new LinkedHashMap<String,CustomUniformBindings>();
      long sourceChars=0;
      var preprocessed=new LinkedHashMap<VirtualPath,String>();
      for(String root:PostProcessCapabilities.ROOTS) {
         var sources=new ArrayList<String>();
         var stages=new LinkedHashMap<String,String>();
         String postRoot=fullscreenRoot(pack,root),worldRoot=WorldProgramPlan.sourceRoot(pack,root);
         for(String stage:FULLSCREEN_STAGES) stages.put(stage,postRoot);
         for (String stage : SkyProgramPlan.STAGES) stages.put(stage, SkyProgramPlan.sourceRoot(pack, root, stage));
         for(String terrain:WorldProgramPlan.TERRAIN_FALLBACKS) {
            if(pack.exists(VirtualPath.parse(worldRoot+"/"+terrain+".fsh"))) { stages.put(terrain,worldRoot);break; }
         }
         for(var layer:TerrainLayerPlan.Layer.values()) {
            for(String candidate:layer.fallbackChain()) {
               if(pack.exists(VirtualPath.parse(worldRoot+"/"+candidate+".vsh"))
                   || pack.exists(VirtualPath.parse(worldRoot+"/"+candidate+".fsh"))) {
                  stages.put(candidate,worldRoot);break;
               }
            }
         }
         if(pack.exists(VirtualPath.parse(worldRoot+"/shadow.vsh"))&&pack.exists(VirtualPath.parse(worldRoot+"/shadow.fsh"))) stages.put("shadow",worldRoot);
         for(var stage:stages.entrySet()) {
            if(!pack.exists(VirtualPath.parse(stage.getValue()+"/"+stage.getKey()+".fsh"))) continue;
            for(String extension:List.of(".vsh",".fsh")) {
               var shader=VirtualPath.parse(stage.getValue()+"/"+stage.getKey()+extension);
               if(!pack.exists(shader)) continue;
               String source=preprocessed.get(shader);
               if(source==null) {
                  source=GlslPreprocessor.preprocess(new ShaderSourceLoader(pack,limits).load(shader).source(),GLSL_ENVIRONMENT);
                  sourceChars=addPreparedSourceChars(sourceChars,source.length(),0);
                  preprocessed.put(shader,source);
               }
               sources.add(source);
            }
         }
         try { result.put(root,CustomUniformBindings.prepare(custom,sources)); }
         catch(ShaderCompatibilityException invalid) { throw new ShaderCompatibilityException(root+": "+invalid.getMessage()); }
      }
      return Map.copyOf(result);
   }

   private static String fullscreenRoot(PackFileSystem pack,String root) throws IOException {
      for(String stage:FULLSCREEN_STAGES) if(pack.exists(VirtualPath.parse(root+"/"+stage+".fsh"))) return root;
      return "shaders";
   }

   /** Opt-in CPU pair diagnostic. Does not enable sky draws or imply GPU support. */
   public Map<String, String> auditSkyPrograms(ShaderPackDescriptor descriptor) throws IOException, ShaderCompatibilityException {
      var results = new LinkedHashMap<String, String>();
      try (var pack = descriptor.open(limits)) {
         var properties = VirtualPath.parse("shaders/shaders.properties");
         var custom = pack.exists(properties) ? NativeCustomUniforms.parse(pack.readUtf8(properties), GLSL_ENVIRONMENT)
             : NativeCustomUniforms.EMPTY;
         long total = 0;
         for (String root : PostProcessCapabilities.ROOTS) {
            for (String stage : List.of("gbuffers_skybasic", "gbuffers_skytextured")) {
               var path = VirtualPath.parse(root + "/" + stage + ".vsh");
               if (!pack.exists(path)) continue;
               String source = GlslPreprocessor.preprocess(new ShaderSourceLoader(pack, limits).load(path).source(), GLSL_ENVIRONMENT);
               total = addPreparedSourceChars(total, source.length(), 0);
               try {
                  var fragmentPath = VirtualPath.parse(root + "/" + stage + ".fsh");
                  if (!pack.exists(fragmentPath)) throw new ShaderCompatibilityException("Missing paired sky fragment shader");
                  String fragment = GlslPreprocessor.preprocess(new ShaderSourceLoader(pack, limits).load(fragmentPath).source(), GLSL_ENVIRONMENT);
                  total = addPreparedSourceChars(total, fragment.length(), 0);
                  var bindings = CustomUniformBindings.prepare(custom, List.of(source, fragment));
                  var prepared = NativeSkyProgram.adapt(source, fragment, WorldProgramPlan.parseTargets(fragment, stage), custom.withBindings(bindings));
                  results.put(path.toString(), "PAIR CPU PREPARED (POSITION_TEX_COLOR only; no runtime): "
                      + prepared.fingerprint() + ", worldInputs=" + prepared.usesWorldMatrices());
               } catch (ShaderCompatibilityException unsupported) {
                  results.put(path.toString(), "UNSUPPORTED: " + unsupported.getMessage());
               }
            }
         }
      }
      return java.util.Collections.unmodifiableMap(results);
   }

   public PreparedPackPipeline prepare(ShaderPackDescriptor descriptor) throws IOException, ShaderCompatibilityException {
      try (PackFileSystem pack = descriptor.open(this.limits)) {
         PackTextureBindings textureBindings = PostProcessCapabilities.validatePack(pack, FULLSCREEN_STAGES);
         Map<String,CustomUniformBindings> customBindings=prepareCustomBindings(pack);
         WorldProgramPlan world = WorldProgramPlan.inspect(pack, PostProcessCapabilities.ROOTS, this.limits, GLSL_ENVIRONMENT,customBindings);
         Map<String, PreparedPackPipeline.Variant> variants = new LinkedHashMap<>();
         Map<String, java.util.Set<Integer>> requiredWorldBuffers = new LinkedHashMap<>();
         Map<String, ColorBufferFormats> formatsByRoot = new LinkedHashMap<>();
         int noiseResolution = 0;
         boolean needsNoise = false;
         long preparedSourceChars = 0L;
         for (var sky : world.skyVariants().values()) for (var entry : sky.programs().values()) {
            if (entry.prepared() != null) preparedSourceChars = addPreparedSourceChars(preparedSourceChars,
                entry.prepared().vertex().source().length(), entry.prepared().fragmentSource().length());
         }
         for (var worldVariant : world.variants().values()) {
            for(var program:worldVariant.preparedNativePrograms())
               preparedSourceChars = addPreparedSourceChars(preparedSourceChars,program.vertexSource().length(),program.fragmentSource().length());
         }
         for (String root : PostProcessCapabilities.ROOTS) {
            var custom=customBindings.get(root);
            String sourceRoot=fullscreenRoot(pack,root);
            if(!root.equals("shaders")&&sourceRoot.equals("shaders")&&WorldProgramPlan.sourceRoot(pack,root).equals("shaders")) continue;
            WorldProgramPlan.Variant worldVariant = world.variant(root);
            EyeBrightnessSettings eyeSettings = new EyeBrightnessSettings();
            if (worldVariant != null) {
               for (var nativeProgram : worldVariant.nativePrograms()) {
                  if (nativeProgram == null) continue;
                  eyeSettings.read(nativeProgram.vertexSource());
                  eyeSettings.read(nativeProgram.fragmentSource());
               }
            }
            ColorBufferFormats.Builder formatBuilder = new ColorBufferFormats.Builder(
               worldVariant == null ? ColorBufferFormats.DEFAULT : worldVariant.formats());
            List<PreparedFinalProgram> programs = new ArrayList<>();
            for (String stageName : FULLSCREEN_STAGES) {
               VirtualPath fragmentPath = VirtualPath.parse(sourceRoot + "/" + stageName + ".fsh");
               if (!pack.exists(fragmentPath)) continue;
               try {
                  String vertexSource = LegacyFinalVertexAdapter.DEFAULT_SOURCE;
                  Map<String, String> vertexOutputs = LegacyFinalVertexAdapter.DEFAULT_OUTPUTS;
                  List<LegacyFinalShaderAdapter.UniformBinding> vertexUniforms = List.of();
                  List<LegacyFinalShaderAdapter.SamplerBinding> vertexSamplers = List.of();
                  VirtualPath vertexPath = VirtualPath.parse(sourceRoot + "/" + stageName + ".vsh");
                  if (pack.exists(vertexPath)) {
                     String vertex = new ShaderSourceLoader(pack, this.limits).load(vertexPath).source();
                     String vertexPreprocessed = GlslPreprocessor.preprocess(vertex, GLSL_ENVIRONMENT);
                     formatBuilder.read(vertexPreprocessed, vertexPath.toString());
                     eyeSettings.read(vertexPreprocessed);
                     noiseResolution = NoiseTextureData.mergeResolution(noiseResolution, vertexPreprocessed);
                     LegacyFinalVertexAdapter.AdaptedVertex adaptedVertex =
                        this.vertexAdapter.adapt(custom.adapt(vertexPreprocessed), textureBindings.forStage(stageName));
                     vertexSource = adaptedVertex.source();
                     vertexOutputs = adaptedVertex.outputs();
                     vertexUniforms = adaptedVertex.uniforms();
                     vertexSamplers = adaptedVertex.samplers();
                  }
                  String fragment = new ShaderSourceLoader(pack, this.limits).load(fragmentPath).source();
                  String source = GlslPreprocessor.preprocess(fragment, GLSL_ENVIRONMENT);
                  formatBuilder.read(source, fragmentPath.toString());
                  eyeSettings.read(source);
                  noiseResolution = NoiseTextureData.mergeResolution(noiseResolution, source);
                  List<Integer> outputs = PostProcessCapabilities.outputBuffers(stageName, source);
                  LegacyFinalShaderAdapter.AdaptedShader adapted = this.adapter.adapt(custom.adapt(source), vertexOutputs, textureBindings.forStage(stageName));
                  if (!vertexUniforms.isEmpty()) {
                     Map<String, LegacyFinalShaderAdapter.UniformBinding> merged = new LinkedHashMap<>();
                     adapted.uniforms().forEach(uniform -> merged.put(uniform.name(), uniform));
                     vertexUniforms.forEach(uniform -> merged.putIfAbsent(uniform.name(), uniform));
                     List<LegacyFinalShaderAdapter.UniformBinding> combined = List.copyOf(merged.values());
                     vertexSource = LegacyFinalShaderAdapter.withUniformBlock(vertexSource, combined);
                     adapted = new LegacyFinalShaderAdapter.AdaptedShader(
                        LegacyFinalShaderAdapter.withUniformBlock(adapted.source(), combined), adapted.samplers(), combined
                     );
                  }
                  if (!vertexSamplers.isEmpty()) {
                     Map<String, LegacyFinalShaderAdapter.SamplerBinding> merged = new LinkedHashMap<>();
                     adapted.samplers().forEach(sampler -> merged.put(sampler.name(), sampler));
                     vertexSamplers.forEach(sampler -> merged.putIfAbsent(sampler.name(), sampler));
                     adapted = new LegacyFinalShaderAdapter.AdaptedShader(adapted.source(), List.copyOf(merged.values()), adapted.uniforms());
                  }
                  if (adapted.samplers().size() > MAX_SAMPLERS_PER_PROGRAM) {
                     throw new ShaderCompatibilityException(stageName + " needs " + adapted.samplers().size()
                        + " distinct texture bindings, but this bridge supports at most "
                        + MAX_SAMPLERS_PER_PROGRAM + " per draw: " + adapted.samplers().stream().limit(16)
                           .map(LegacyFinalShaderAdapter.SamplerBinding::name).collect(java.util.stream.Collectors.joining(", ")));
                  }
                  for (var sampler : adapted.samplers()) {
                     if (sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.SHADOW_COLOR
                         && (worldVariant == null || !worldVariant.shadowProgram()
                             || !worldVariant.shadowTargets().contains(sampler.colorIndex())
                             || sampler.colorIndex() == 1 && worldVariant.nativeShadow() == null))
                        throw new ShaderCompatibilityException(stageName + " requires shadowcolor" + sampler.colorIndex()
                            + ", but no supported shadow producer writes that attachment"
                            + (worldVariant == null ? " (no world program)" : ": "
                                + (worldVariant.nativeShadow() == null ? worldVariant.nativeShadowIssue()
                                    : "native shadow outputs " + worldVariant.shadowTargets())));
                  }
                  preparedSourceChars = addPreparedSourceChars(preparedSourceChars, vertexSource.length(), adapted.source().length());
                  needsNoise |= adapted.samplers().stream().anyMatch(
                     binding -> binding.texture() == LegacyFinalShaderAdapter.TextureSemantic.NOISE);
                  programs.add(new PreparedFinalProgram(descriptor, stageName, vertexSource,
                     adapted, outputs));
               } catch (ShaderCompatibilityException exception) {
                  throw new ShaderCompatibilityException(fragmentPath + ": " + exception.getMessage());
               }
            }
            if (!programs.isEmpty()) {
               ColorBufferFormats formats = formatBuilder.build();
               formatsByRoot.put(root, formats);
               programs = programs.stream().map(program -> program.withFormats(formats)).toList();
               if (!formats.shouldClear(0) || formats.clearColors().containsKey(0)) {
                  throw new ShaderCompatibilityException(root + ": custom clearing/history of the borrowed vanilla colortex0 is not implemented");
               }
               java.util.Set<Integer> usedBuffers = new java.util.HashSet<>();
               for (PreparedFinalProgram program : programs) {
                  usedBuffers.addAll(program.outputBuffers());
                  for (var sampler : program.fragment().samplers()) {
                     if (sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.COLOR) usedBuffers.add(sampler.colorIndex());
                     if (sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.DEPTH
                         && sampler.colorIndex() == 1 && worldVariant == null)
                        throw new ShaderCompatibilityException(root + ": post depthtex1 requires the native world snapshot bridge");
                  }
               }
               java.util.LinkedHashSet<Integer> initialBuffers = new java.util.LinkedHashSet<>(
                  worldVariant == null ? java.util.Set.of() : worldVariant.initialColorBuffers());
               for (int buffer : usedBuffers) {
                  if (buffer <= 0) continue;
                  if (worldVariant == null && (!formats.shouldClear(buffer) || formats.clearColors().containsKey(buffer))) {
                     throw new ShaderCompatibilityException(root + ": auxiliary clearing/history currently requires the native world bridge");
                  }
                  if (worldVariant != null && !formats.shouldClear(buffer)) initialBuffers.add(buffer);
               }
               java.util.LinkedHashSet<Integer> availableBuffers = new java.util.LinkedHashSet<>(initialBuffers);
               availableBuffers.add(0);
               for (PreparedFinalProgram program : programs) {
                  for (var sampler : program.fragment().samplers()) {
                     if (worldVariant != null && sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.COLOR && sampler.colorIndex() > 0
                        && !availableBuffers.contains(sampler.colorIndex())) {
                        initialBuffers.add(sampler.colorIndex());
                        availableBuffers.add(sampler.colorIndex());
                     }
                  }
                  program.outputBuffers().stream().filter(index -> index >= 0).forEach(availableBuffers::add);
               }
               requiredWorldBuffers.put(root, java.util.Set.copyOf(initialBuffers));
               variants.put(root, new PreparedPackPipeline.Variant(programs, ColorBufferPlan.create(programs, initialBuffers, formats), eyeSettings.ticks(),custom));
            }
         }
         if (variants.isEmpty()) {
            throw new ShaderCompatibilityException("Pack has no supported deferred, composite or final programs");
         }
         boolean nativeNoise = false;
         for (var worldVariant : world.variants().values()) {
            for (var nativeProgram : worldVariant.nativePrograms()) {
               if (nativeProgram == null) continue;
               nativeNoise |= nativeProgram.usesNoise();
               noiseResolution = NoiseTextureData.mergeResolution(noiseResolution, nativeProgram.vertexSource());
               noiseResolution = NoiseTextureData.mergeResolution(noiseResolution, nativeProgram.fragmentSource());
            }
         }
         String worldNoiseImageKey = nativeNoise ? textureBindings.forStage("final").get("noisetex") : null;
         needsNoise |= nativeNoise && worldNoiseImageKey == null;
         NoiseTextureData noise = needsNoise
            ? NoiseTextureData.generate(noiseResolution == 0 ? NoiseTextureData.DEFAULT_RESOLUTION : noiseResolution) : null;
         Map<String, PackImageData> images = new LinkedHashMap<>();
         long imageBytes = 0;
         for (PreparedPackPipeline.Variant variant : variants.values()) {
            for (PreparedFinalProgram program : variant.programs()) {
               for (var sampler : program.fragment().samplers()) {
                  if (sampler.texture() != LegacyFinalShaderAdapter.TextureSemantic.CUSTOM || images.containsKey(sampler.imageKey())) continue;
                  PackImageData image = PackImageData.load(pack, VirtualPath.parse(sampler.imageKey()), PackImageData.MAX_TOTAL_BYTES - imageBytes);
                  images.put(sampler.imageKey(), image);
                  imageBytes += image.byteSize();
               }
            }
         }
         if (worldNoiseImageKey != null && !images.containsKey(worldNoiseImageKey)) {
            PackImageData image = PackImageData.load(pack, VirtualPath.parse(worldNoiseImageKey), PackImageData.MAX_TOTAL_BYTES - imageBytes);
            images.put(worldNoiseImageKey, image);
         }
         world = world.withRequiredColorBuffers(requiredWorldBuffers).withFormats(formatsByRoot);
         boolean materialIds = world.variants().values().stream().anyMatch(v ->
            v.nativePrograms().stream().anyMatch(NativeTerrainProgram::usesMaterialIds));
         BlockMaterialMap materials = world.variants().isEmpty() ? BlockMaterialMap.EMPTY
             : BlockMaterialMap.load(pack, GLSL_ENVIRONMENT, materialIds);
         return new PreparedPackPipeline(descriptor, variants, noise, images, world, materials, worldNoiseImageKey,
             ItemMaterialMap.load(pack,GLSL_ENVIRONMENT));
      }
   }

   static long addPreparedSourceChars(long current, int vertexChars, int fragmentChars) throws ShaderCompatibilityException {
      if (current < 0L || vertexChars < 0 || fragmentChars < 0) {
         throw new IllegalArgumentException("Prepared source sizes must be nonnegative");
      }
      final long total;
      try {
         total = Math.addExact(current, Math.addExact((long)vertexChars, fragmentChars));
      } catch (ArithmeticException exception) {
         throw new ShaderCompatibilityException("Prepared shader source size overflow");
      }
      if (total > MAX_PREPARED_SOURCE_CHARS) {
         throw new ShaderCompatibilityException("Prepared shader sources exceed the "
            + MAX_PREPARED_SOURCE_CHARS + "-character per-pack safety budget");
      }
      return total;
   }

   public PackShaderPipeline compile(PreparedPackPipeline prepared) throws ShaderCompatibilityException {
      RenderSystem.assertOnRenderThread();
      TextureBindingCapacity.requireSupported(prepared.programs());
      if(dev.oculusforge.render.world.SceneColorFormat.ENABLED) {
         FullscreenColorTransfer.compile();
         for(var variant:prepared.world().variants().values())
            if(dev.oculusforge.render.world.SceneColorFormat.requiresCapture(variant.formats()))
               FullscreenColorTransfer.compile(variant.formats().get(0));
      }
      // Fail during pack activation, before the framegraph allocates any targets.
      for (ColorBufferFormat format : ColorBufferFormat.values()) format.validateTextureMapping();
      java.util.Set<String> requested = new java.util.HashSet<>();
      for (PreparedFinalProgram program : prepared.programs()) requested.add(programFingerprint(program));
      descriptors.requireCapacity(requested);
      List<PackFinalProgram> allocated = new ArrayList<>();
      Map<String, PackShaderPipeline.Variant> variants = new LinkedHashMap<>();
      PackGpuTexture noise = null;
      Map<String, PackGpuTexture> images = new LinkedHashMap<>();
      WorldRenderPipeline world = null;
      try {
         for (String root : PostProcessCapabilities.ROOTS) {
            PreparedPackPipeline.Variant variant = prepared.variants().get(root);
            if (variant == null) continue;
            if (variant.buffers().presentedSlot() != ColorBufferPlan.MAIN
                && variant.buffers().scratchFormats().get(variant.buffers().presentedSlot()) != ColorBufferFormat.RGBA8) {
               FullscreenColorTransfer.compile();
            }
            List<PackFinalProgram> programs = new ArrayList<>();
            for (PreparedFinalProgram program : variant.programs()) {
               PackFinalProgram compiled = this.compileProgram(program,prepared.items());
               allocated.add(compiled);
               programs.add(compiled);
            }
            variants.put(root, new PackShaderPipeline.Variant(programs, variant.buffers(), new EyeBrightnessSmoother(variant.eyeBrightnessHalfLife()),variant.customUniforms().newState()));
         }
         if (prepared.noise() != null) noise = PackGpuTexture.create(prepared.noise());
         for (var entry : prepared.images().entrySet()) images.put(entry.getKey(), PackGpuTexture.create(entry.getKey(), entry.getValue()));
         if (!prepared.world().variants().isEmpty()) world = WorldRenderPipeline.compile(prepared.world(), worldDescriptors,
             prepared.materials(), prepared.worldNoiseImageKey() == null ? noise : images.get(prepared.worldNoiseImageKey()),prepared.items());
         if (world != null) for (var entry : variants.entrySet()) {
            boolean needsOpaque = entry.getValue().programs().stream().flatMap(p -> p.samplers().stream())
                .anyMatch(s -> s.texture() == LegacyFinalShaderAdapter.TextureSemantic.DEPTH && s.colorIndex() == 1);
            if (needsOpaque) world.requirePostOpaqueDepth(entry.getKey());
         }
         return new PackShaderPipeline(prepared.pack().id(), variants, noise, images, world);
      } catch (RuntimeException | ShaderCompatibilityException | Error exception) {
         for (PackGpuTexture image : images.values()) {
            try { image.close(); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
         }
         if (noise != null) {
            try { noise.close(); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
         }
         if (world != null) {
            try { world.close(); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
         }
         for (PackFinalProgram program : allocated) {
            try {
               program.close();
            } catch (RuntimeException cleanupFailure) {
               exception.addSuppressed(cleanupFailure);
            }
         }
         throw exception;
      }
   }

   private PackFinalProgram compileProgram(PreparedFinalProgram prepared,ItemMaterialMap items) throws ShaderCompatibilityException {
      String fingerprint = programFingerprint(prepared);
      String root = "pack/" + fingerprint + "/" + prepared.stageName();
      Identifier vertexId = Identifier.fromNamespaceAndPath("oculus", root + "_vertex");
      Identifier fragmentId = Identifier.fromNamespaceAndPath("oculus", root + "_fragment");
      Identifier pipelineId = Identifier.fromNamespaceAndPath("oculus", root);
      Builder bindings = BindGroupLayout.builder();
      prepared.fragment().samplers().forEach(sampler -> bindings.withSampler(sampler.name()));
      boolean hasUniforms = !prepared.fragment().uniforms().isEmpty();
      if (hasUniforms) {
         bindings.withUniform("OculusLegacyUniforms", UniformType.UNIFORM_BUFFER);
      }

      RenderPipeline pipeline = descriptors.getOrCreate(fingerprint, () -> {
         var builder = RenderPipeline.builder(new Snippet[0])
            .withLocation(pipelineId)
            .withVertexShader(vertexId)
            .withFragmentShader(fragmentId)
            .withBindGroupLayout(bindings.build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false);
         for (int index = 0; index < prepared.outputBuffers().size(); index++) {
            builder.withColorTargetState(index, new ColorTargetState(java.util.Optional.empty(),
               prepared.formats().get(prepared.outputBuffers().get(index)).gpu(),
               prepared.formats().get(prepared.outputBuffers().get(index)).writeMask()));
         }
         return builder.build();
      });
      ShaderSource source = (id, type) -> shaderSource(id, type, vertexId, fragmentId, prepared.vertexSource(), prepared.fragment().source());
      CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
      if (!compiled.isValid()) {
         throw new ShaderCompatibilityException(
            "OpenGL rejected shaders/"
               + prepared.stageName()
               + ".fsh from '"
               + prepared.pack().displayName()
               + "' at stage '"
               + prepared.stageName()
               + "'. The compiler log is in latest.log."
         );
      } else {
         MappableRingBuffer uniformBuffer = hasUniforms
            ? new MappableRingBuffer(
               () -> "Oculus " + prepared.pack().displayName() + " legacy uniforms", 130, PackFinalProgram.uniformBufferSize(prepared.fragment().uniforms())
            )
            : null;
         try {
         return new PackFinalProgram(
            prepared.pack().id(), prepared.stageName(), pipeline, prepared.fragment().samplers(), prepared.fragment().uniforms(), uniformBuffer,
            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(),items
         );
         } catch (RuntimeException | Error failure) {
            if (uniformBuffer != null) {
               try { uniformBuffer.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
         }
      }
   }

   private static List<String> fullscreenStages() {
      List<String> stages = new ArrayList<>(33);
      stages.add("deferred");

      for (int index = 1; index <= 15; index++) {
         stages.add("deferred" + index);
      }

      stages.add("composite");

      for (int index = 1; index <= 15; index++) {
         stages.add("composite" + index);
      }

      stages.add("final");
      return List.copyOf(stages);
   }

   private static String shaderSource(
      Identifier requested, ShaderType type, Identifier vertexId, Identifier fragmentId, String vertexSource, String fragmentSource
   ) {
      if (type == ShaderType.VERTEX && requested.equals(vertexId)) {
         return vertexSource;
      } else {
         return type == ShaderType.FRAGMENT && requested.equals(fragmentId) ? fragmentSource : null;
      }
   }

   private static String programFingerprint(PreparedFinalProgram prepared) throws ShaderCompatibilityException {
      return fingerprint(prepared.stageName() + "\u0000" + prepared.outputBuffers()
         + "\u0000" + prepared.outputBuffers().stream().map(prepared.formats()::get).toList()
         + "\u0000" + prepared.vertexSource() + "\u0000" + prepared.fragment().source());
   }

   private static String fingerprint(String source) throws ShaderCompatibilityException {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(digest);
      } catch (NoSuchAlgorithmException var2) {
         throw new ShaderCompatibilityException("This Java runtime does not provide SHA-256");
      }
   }
}
