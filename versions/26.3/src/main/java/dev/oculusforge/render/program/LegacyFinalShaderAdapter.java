package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LegacyFinalShaderAdapter {
   private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#[\\t ]*version[\\t ]+\\d+(?:[\\t ]+\\w+)?[\\t ]*$");
   private static final Pattern SAMPLER = Pattern.compile("(?m)^[\\t ]*uniform[\\t ]+(sampler2D|sampler2DShadow)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*;[\\t ]*(?://.*)?$");
   private static final Pattern UNIFORM = Pattern.compile("(?m)^[\\t ]*uniform[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*;[\\t ]*(?://.*)?$");
   private static final Pattern DEFAULTED_UNIFORM = Pattern.compile(
      "(?m)^[\\t ]*uniform[\\t ]+(bool|float|int|vec[234]|ivec[234])[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*=[\\t ]*([^;\\n]+);"
   );
   private static final Pattern UNIFORM_LIST = Pattern.compile(
      "(?m)^[\\t ]*uniform[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*(?:[\\t ]*,[\\t ]*[A-Za-z_][A-Za-z0-9_]*)+)[\\t ]*;[\\t ]*(?://.*)?$"
   );
   private static final Pattern FRAG_DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*]");
   private static final Pattern LEGACY_VARYING = Pattern.compile("\\bvarying\\b");
   private static final Pattern VARYING_DECLARATION = Pattern.compile(
      "(?m)^[\\t ]*varying[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*;[\\t ]*(?://.*)?$"
   );
   private static final Pattern ANY_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*(?:layout[\\t ]*\\([^\\n)]*\\)[\\t ]*)?uniform\\b[^\\n;{]*(?:;|\\{)");
   private static final Pattern LEGACY_ATTRIBUTE = Pattern.compile("\\battribute\\b");
   private static final Pattern TEXTURE_2D_LOD = Pattern.compile("\\btexture2DLod\\b");
   private static final Pattern TEXTURE_2D = Pattern.compile("\\btexture2D\\b");
   private static final Pattern FRAG_COLOR = Pattern.compile("\\bgl_FragColor\\b");
   private static final Pattern UNIFORM_BLOCK = Pattern.compile(
      "(?s)layout\\s*\\(\\s*std140\\s*\\)\\s*uniform\\s+OculusLegacyUniforms\\s*\\{.*?}\\s*;\\s*"
   );
   private static final Map<String, Integer> COLOR_ALIASES = Map.ofEntries(
      Map.entry("gcolor", 0), Map.entry("gdepth", 1), Map.entry("gnormal", 2),
      Map.entry("composite", 3), Map.entry("gaux1", 4), Map.entry("gaux2", 5),
      Map.entry("gaux3", 6), Map.entry("gaux4", 7), Map.entry("insampler", 0)
   );
   private static final Map<String, LegacyFinalShaderAdapter.UniformSemantic> UNIFORM_SEMANTICS = Map.ofEntries(
      Map.entry("viewWidth", LegacyFinalShaderAdapter.UniformSemantic.VIEW_WIDTH),
      Map.entry("viewHeight", LegacyFinalShaderAdapter.UniformSemantic.VIEW_HEIGHT),
      Map.entry("aspectRatio", LegacyFinalShaderAdapter.UniformSemantic.ASPECT_RATIO),
      Map.entry("frameTime", LegacyFinalShaderAdapter.UniformSemantic.FRAME_TIME),
      Map.entry("frameTimeSmooth", LegacyFinalShaderAdapter.UniformSemantic.FRAME_TIME_SMOOTH),
      Map.entry("frameTimeCounter", LegacyFinalShaderAdapter.UniformSemantic.FRAME_TIME_COUNTER),
      Map.entry("frameCounter", LegacyFinalShaderAdapter.UniformSemantic.FRAME_COUNTER),
      Map.entry("worldTime", LegacyFinalShaderAdapter.UniformSemantic.WORLD_TIME),
      Map.entry("worldDay", LegacyFinalShaderAdapter.UniformSemantic.WORLD_DAY),
      Map.entry("moonPhase", LegacyFinalShaderAdapter.UniformSemantic.MOON_PHASE),
      Map.entry("rainStrength", LegacyFinalShaderAdapter.UniformSemantic.RAIN_STRENGTH),
      Map.entry("wetness", LegacyFinalShaderAdapter.UniformSemantic.WETNESS),
      Map.entry("eyeAltitude", LegacyFinalShaderAdapter.UniformSemantic.EYE_ALTITUDE),
      Map.entry("near", LegacyFinalShaderAdapter.UniformSemantic.NEAR),
      Map.entry("far", LegacyFinalShaderAdapter.UniformSemantic.FAR),
      Map.entry("sunAngle", LegacyFinalShaderAdapter.UniformSemantic.SUN_ANGLE),
      Map.entry("timeAngle", LegacyFinalShaderAdapter.UniformSemantic.TIME_ANGLE),
      Map.entry("screenBrightness", LegacyFinalShaderAdapter.UniformSemantic.SCREEN_BRIGHTNESS),
      Map.entry("cloudHeight", LegacyFinalShaderAdapter.UniformSemantic.CLOUD_HEIGHT),
      Map.entry("atlasSize", LegacyFinalShaderAdapter.UniformSemantic.ATLAS_SIZE),
      Map.entry("heldItemId", LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID),
      Map.entry("heldItemId2", LegacyFinalShaderAdapter.UniformSemantic.HELD_ITEM_ID_2),
      Map.entry("isEyeInWater", LegacyFinalShaderAdapter.UniformSemantic.IS_EYE_IN_WATER),
      Map.entry("cameraPosition", LegacyFinalShaderAdapter.UniformSemantic.CAMERA_POSITION),
      Map.entry("previousCameraPosition", LegacyFinalShaderAdapter.UniformSemantic.PREVIOUS_CAMERA_POSITION),
      Map.entry("fogColor", LegacyFinalShaderAdapter.UniformSemantic.FOG_COLOR),
      Map.entry("fogStart", LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_START),
      Map.entry("fogEnd", LegacyFinalShaderAdapter.UniformSemantic.LEGACY_FOG_END),
      Map.entry("skyColor", LegacyFinalShaderAdapter.UniformSemantic.SKY_COLOR),
      Map.entry("sunPosition", LegacyFinalShaderAdapter.UniformSemantic.SUN_POSITION),
      Map.entry("endFlashIntensity", LegacyFinalShaderAdapter.UniformSemantic.END_FLASH_INTENSITY),
      Map.entry("endFlashPosition", LegacyFinalShaderAdapter.UniformSemantic.END_FLASH_POSITION),
      Map.entry("nightVision", LegacyFinalShaderAdapter.UniformSemantic.NIGHT_VISION),
      Map.entry("blindFactor", LegacyFinalShaderAdapter.UniformSemantic.BLIND_FACTOR),
      Map.entry("blindness", LegacyFinalShaderAdapter.UniformSemantic.BLIND_FACTOR),
      Map.entry("darknessFactor", LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_FACTOR),
      Map.entry("darknessLightFactor", LegacyFinalShaderAdapter.UniformSemantic.DARKNESS_LIGHT_FACTOR),
      Map.entry("maxBlindnessDarkness", LegacyFinalShaderAdapter.UniformSemantic.MAX_BLINDNESS_DARKNESS),
      Map.entry("shadowFade", LegacyFinalShaderAdapter.UniformSemantic.SHADOW_FADE),
      Map.entry("timeBrightness", LegacyFinalShaderAdapter.UniformSemantic.TIME_BRIGHTNESS),
      Map.entry("eyeBrightnessSmooth", LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_SMOOTH),
      Map.entry("eyeBrightness", LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS),
      Map.entry("eyeBrightnessM", LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_MAX),
      Map.entry("eyeBrightnessM2", LegacyFinalShaderAdapter.UniformSemantic.EYE_BRIGHTNESS_MAX),
      Map.entry("inDry", LegacyFinalShaderAdapter.UniformSemantic.IN_DRY),
      Map.entry("inSnowy", LegacyFinalShaderAdapter.UniformSemantic.IN_SNOWY),
      Map.entry("inRainy", LegacyFinalShaderAdapter.UniformSemantic.IN_RAINY),
      Map.entry("inPaleGarden", LegacyFinalShaderAdapter.UniformSemantic.IN_PALE_GARDEN),
      Map.entry("centerDepthSmooth", LegacyFinalShaderAdapter.UniformSemantic.CENTER_DEPTH_SMOOTH),
      Map.entry("velocity", LegacyFinalShaderAdapter.UniformSemantic.CAMERA_VELOCITY),
      Map.entry("isDesert", LegacyFinalShaderAdapter.UniformSemantic.IS_DESERT),
      Map.entry("isMesa", LegacyFinalShaderAdapter.UniformSemantic.IS_MESA),
      Map.entry("isCold", LegacyFinalShaderAdapter.UniformSemantic.IS_COLD),
      Map.entry("isSwamp", LegacyFinalShaderAdapter.UniformSemantic.IS_SWAMP),
      Map.entry("isMushroom", LegacyFinalShaderAdapter.UniformSemantic.IS_MUSHROOM),
      Map.entry("isSavanna", LegacyFinalShaderAdapter.UniformSemantic.IS_SAVANNA),
      Map.entry("isJungle", LegacyFinalShaderAdapter.UniformSemantic.IS_JUNGLE),
      Map.entry("cameraPositionInt", LegacyFinalShaderAdapter.UniformSemantic.CAMERA_POSITION_INT),
      Map.entry("cameraPositionFract", LegacyFinalShaderAdapter.UniformSemantic.CAMERA_POSITION_FRACT),
      Map.entry("previousCameraPositionFract", LegacyFinalShaderAdapter.UniformSemantic.PREVIOUS_CAMERA_POSITION_FRACT),
      Map.entry("gbufferProjection", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PROJECTION),
      Map.entry("gbufferProjectionInverse", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PROJECTION_INVERSE),
      Map.entry("gbufferModelView", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_MODEL_VIEW),
      Map.entry("gbufferModelViewInverse", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_MODEL_VIEW_INVERSE),
      Map.entry("gbufferPreviousProjection", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PREVIOUS_PROJECTION),
      Map.entry("gbufferPreviousProjectionInverse", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PREVIOUS_PROJECTION_INVERSE),
      Map.entry("gbufferPreviousModelView", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PREVIOUS_MODEL_VIEW),
      Map.entry("gbufferPreviousModelViewInverse", LegacyFinalShaderAdapter.UniformSemantic.GBUFFER_PREVIOUS_MODEL_VIEW_INVERSE),
      Map.entry("shadowProjection", LegacyFinalShaderAdapter.UniformSemantic.SHADOW_PROJECTION),
      Map.entry("shadowProjectionInverse", LegacyFinalShaderAdapter.UniformSemantic.SHADOW_PROJECTION_INVERSE),
      Map.entry("shadowModelView", LegacyFinalShaderAdapter.UniformSemantic.SHADOW_MODEL_VIEW),
      Map.entry("shadowModelViewInverse", LegacyFinalShaderAdapter.UniformSemantic.SHADOW_MODEL_VIEW_INVERSE),
      Map.entry("rainFactor", LegacyFinalShaderAdapter.UniformSemantic.RAIN_FACTOR),
      Map.entry("inBasaltDeltas", LegacyFinalShaderAdapter.UniformSemantic.IN_BASALT_DELTAS),
      Map.entry("isBasalt", LegacyFinalShaderAdapter.UniformSemantic.IN_BASALT_DELTAS),
      Map.entry("inCrimsonForest", LegacyFinalShaderAdapter.UniformSemantic.IN_CRIMSON_FOREST),
      Map.entry("isCrimson", LegacyFinalShaderAdapter.UniformSemantic.IN_CRIMSON_FOREST),
      Map.entry("inNetherWastes", LegacyFinalShaderAdapter.UniformSemantic.IN_NETHER_WASTES),
      Map.entry("inSoulValley", LegacyFinalShaderAdapter.UniformSemantic.IN_SOUL_VALLEY),
      Map.entry("isValley", LegacyFinalShaderAdapter.UniformSemantic.IN_SOUL_VALLEY),
      Map.entry("inWarpedForest", LegacyFinalShaderAdapter.UniformSemantic.IN_WARPED_FOREST),
      Map.entry("isWarped", LegacyFinalShaderAdapter.UniformSemantic.IN_WARPED_FOREST)
   );

   public LegacyFinalShaderAdapter.AdaptedShader adapt(String input) throws ShaderCompatibilityException {
      return this.adapt(input, LegacyFinalVertexAdapter.DEFAULT_OUTPUTS);
   }

   public LegacyFinalShaderAdapter.AdaptedShader adapt(String input, Map<String, String> vertexOutputs) throws ShaderCompatibilityException {
      return adapt(input, vertexOutputs, Map.of());
   }

   public LegacyFinalShaderAdapter.AdaptedShader adapt(String input, Map<String, String> vertexOutputs,
      Map<String, String> customTextures) throws ShaderCompatibilityException {
      if (input != null && !input.isBlank()) {
         String source = input.replace("\r\n", "\n").replace('\r', '\n');
         if (!source.isEmpty() && source.charAt(0) == '\ufeff') {
            source = source.substring(1);
         }

         source = withoutComments(source);
         source = NativeWorldBindings.normalizeCloudHeightDefault(source);
         source = ColorBufferFormats.stripFormatDeclarations(source);
         if (LEGACY_ATTRIBUTE.matcher(withoutComments(source)).find()) {
            throw new ShaderCompatibilityException("Fragment shader uses the vertex-only 'attribute' qualifier");
         } else {
            validateVaryings(source, vertexOutputs);
            var fog=LegacyFogAccess.adapt(source,false);
            source=fog.source();
            source = normalizeUniformLists(source);
            source = CenterDepthInput.adapt(source);
            NativeWorldBindings.validateLiveDeclarations(source);
            source = DEFAULTED_UNIFORM.matcher(source).replaceAll("const $1 $2 = $3;");
            source = stripUnusedUniformDeclarations(source);
            List<LegacyFinalShaderAdapter.SamplerBinding> samplers = parseSamplers(source, customTextures);
            boolean shadowCompare = SAMPLER.matcher(source).results().anyMatch(match -> "sampler2DShadow".equals(match.group(1)));
            List<LegacyFinalShaderAdapter.UniformBinding> uniforms = parseUniforms(source);
            if (fog.used()) {
               List<LegacyFinalShaderAdapter.UniformBinding> enriched = new ArrayList<>(uniforms);
               enriched.addAll(LegacyFogAccess.POST_BINDINGS);
               uniforms = List.copyOf(enriched);
            }
            source = removeLooseUniforms(source, uniforms);
            validateNoUnmappedUniforms(source, samplers);
            java.util.SortedSet<Integer> legacyOutputs = new java.util.TreeSet<>();
            Matcher fragData = FRAG_DATA.matcher(withoutComments(source));
            while (fragData.find()) {
               int index = Integer.parseInt(fragData.group(1));
               if (index > 7) throw new ShaderCompatibilityException("Fragment shader writes gl_FragData[" + index + "] beyond the MRT limit");
               legacyOutputs.add(index);
            }
            if (FRAG_COLOR.matcher(source).find()) legacyOutputs.add(0);
            source = LEGACY_VARYING.matcher(source).replaceAll("in");
            source = adaptShadowSampling(source);
            source = adaptDepthSampling(source, samplers);
            source = TEXTURE_2D_LOD.matcher(source).replaceAll("textureLod");
            source = TEXTURE_2D.matcher(source).replaceAll("texture");
            CanonicalSamplers canonical = canonicalizeSamplers(source, samplers);
            source = canonical.source();
            samplers = canonical.samplers();
            for (int index : legacyOutputs) {
               source = source.replaceAll("\\bgl_FragData\\s*\\[\\s*" + index + "\\s*]", "oculus_FragColor" + index);
            }
            source = FRAG_COLOR.matcher(source).replaceAll("oculus_FragColor0");
            boolean samplesDepth = samplers.stream().anyMatch(binding -> binding.texture() == LegacyFinalShaderAdapter.TextureSemantic.DEPTH
                || binding.texture() == LegacyFinalShaderAdapter.TextureSemantic.SHADOW_DEPTH);
            String declarations = declarations(uniforms, legacyOutputs, samplesDepth, shadowCompare);
            Matcher version = VERSION.matcher(source);
            if (version.find()) {
               source = version.replaceFirst(Matcher.quoteReplacement("#version 330 core\n" + declarations));
            } else {
               source = "#version 330 core\n" + declarations + source;
            }

            var active = GlslFunctionReachability.activeSamplers(source, samplers);
            return new LegacyFinalShaderAdapter.AdaptedShader(active.source(), active.samplers(), uniforms);
         }
      } else {
         throw new ShaderCompatibilityException("final.fsh is empty");
      }
   }

   private static String normalizeUniformLists(String source) {
      Matcher matcher = UNIFORM_LIST.matcher(source);
      StringBuilder result = new StringBuilder(source.length());
      while (matcher.find()) {
         StringBuilder replacement = new StringBuilder();
         for (String name : matcher.group(2).split("\\s*,\\s*")) {
            replacement.append("uniform ").append(matcher.group(1)).append(' ').append(name).append(";\n");
         }
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
      }
      matcher.appendTail(result);
      return result.toString();
   }

   private static String stripUnusedUniformDeclarations(String source) {
      Matcher declarations = UNIFORM.matcher(source);
      String codeOnly = declarations.replaceAll("");
      Matcher matcher = UNIFORM.matcher(source);
      StringBuilder result = new StringBuilder(source.length());
      while (matcher.find()) {
         String name = matcher.group(2);
         if (!Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(withoutComments(codeOnly)).find()) {
            matcher.appendReplacement(result, "");
         }
      }
      matcher.appendTail(result);
      return result.toString();
   }

   private static List<LegacyFinalShaderAdapter.SamplerBinding> parseSamplers(String source, Map<String, String> customTextures) throws ShaderCompatibilityException {
      LinkedHashMap<String, LegacyFinalShaderAdapter.SamplerBinding> samplers = new LinkedHashMap<>();
      List<String> unsupported = new ArrayList<>();
      Matcher matcher = SAMPLER.matcher(source);

      while (matcher.find()) {
         String type = matcher.group(1);
         String name = matcher.group(2);
         SamplerBinding binding = samplerBinding(type, name, customTextures);
         if (binding == null) {
            unsupported.add(name);
            continue;
         }
         samplers.putIfAbsent(name, binding);
      }

      if (!unsupported.isEmpty()) {
         throw new ShaderCompatibilityException("Unsupported post-processing texture uniforms " + unsupported + "; use supported color/depth/shadow samplers or an explicit supported PNG binding");
      }
      return List.copyOf(samplers.values());
   }

   static SamplerBinding samplerBinding(String type, String name, Map<String, String> customTextures)
      throws ShaderCompatibilityException {
      if (!"sampler2D".equals(type) && !"sampler2DShadow".equals(type)) {
         throw new ShaderCompatibilityException("Unsupported sampler type " + type + " for " + name);
      }
      String image = customTextures.get(name);
      if(name.equals(CenterDepthInput.SAMPLER)) {
         if(!type.equals("sampler2D")||image!=null)throw new ShaderCompatibilityException("Invalid internal center-depth binding");
         return new SamplerBinding(name,TextureSemantic.CENTER_DEPTH,-1);
      }
      if (image != null) return new SamplerBinding(name, TextureSemantic.CUSTOM, -1, image);
      String key = name.toLowerCase(Locale.ROOT);
      LegacyFinalShaderAdapter.TextureSemantic semantic = textureSemantic(key);
      if (semantic == null) return null;
      if ("gdepth".equals(key)) {
         throw new ShaderCompatibilityException("The gdepth alias requires an implicit RGBA32F format upgrade that is not implemented; use an explicit colortex format declaration");
      }
      int index = semantic == TextureSemantic.COLOR ? colorIndex(key)
         : semantic == TextureSemantic.DEPTH ? switch(key) {case "depthtex1" -> 1; case "depthtex2" -> 2; default -> 0;}
         : semantic == TextureSemantic.SHADOW_DEPTH ? ("shadowtex1".equals(key) ? 1 : 0)
         : semantic == TextureSemantic.SHADOW_COLOR ? ("shadowcolor1".equals(key) ? 1 : 0) : -1;
      return new SamplerBinding(name, semantic, index);
   }

   private static LegacyFinalShaderAdapter.TextureSemantic textureSemantic(String name) {
      if ("noisetex".equals(name)) return TextureSemantic.NOISE;
      if (COLOR_ALIASES.containsKey(name) || name.matches("colortex(?:[0-9]|1[0-5])")) {
         return TextureSemantic.COLOR;
      }
      if (name.matches("depthtex[0-2]") || "gdepthtex".equals(name)) return TextureSemantic.DEPTH;
      if (name.matches("shadowtex[01]") || "watershadow".equals(name)) return TextureSemantic.SHADOW_DEPTH;
      if (name.matches("shadowcolor[01]")) return TextureSemantic.SHADOW_COLOR;
      return null;
   }

   private static int colorIndex(String name) {
      Integer alias = COLOR_ALIASES.get(name);
      return alias != null ? alias : Integer.parseInt(name.substring("colortex".length()));
   }

   private static List<LegacyFinalShaderAdapter.UniformBinding> parseUniforms(String source) throws ShaderCompatibilityException {
      LinkedHashMap<String, LegacyFinalShaderAdapter.UniformBinding> uniforms = new LinkedHashMap<>();
      List<String> unsupported = new ArrayList<>();
      Matcher matcher = UNIFORM.matcher(source);

      while (matcher.find()) {
         String type = matcher.group(1);
         String name = matcher.group(2);
         if (!type.startsWith("sampler")) {
            LegacyFinalShaderAdapter.UniformSemantic semantic = uniformSemantic(name);
            if (semantic == null) {
               unsupported.add(type + " " + name);
               continue;
            }

            if (!semantic.type().glsl().equals(type)) {
               throw new ShaderCompatibilityException("Uniform '" + name + "' must be declared as " + semantic.type().glsl() + ", not " + type);
            }

            uniforms.putIfAbsent(name, new LegacyFinalShaderAdapter.UniformBinding(name, semantic));
         }
      }

      if (!unsupported.isEmpty()) {
         throw new ShaderCompatibilityException("Unsupported final-pass uniforms " + unsupported + "; Oculus will not substitute incorrect defaults");
      }
      return List.copyOf(uniforms.values());
   }

   static LegacyFinalShaderAdapter.UniformSemantic uniformSemantic(String name) {
      if(CustomUniformBindings.index(name)>=0) return UniformSemantic.CUSTOM_FLOAT;
      return UNIFORM_SEMANTICS.get(name);
   }

   static String withUniformBlock(String source, List<LegacyFinalShaderAdapter.UniformBinding> uniforms) {
      String stripped = UNIFORM_BLOCK.matcher(source).replaceFirst("");
      if (uniforms.isEmpty()) return stripped;
      String block = uniformBlock(uniforms);
      Matcher version = VERSION.matcher(stripped);
      return version.find() ? version.replaceFirst(Matcher.quoteReplacement(version.group() + "\n" + block)) : block + stripped;
   }

   private static String uniformBlock(List<UniformBinding> uniforms) {
      StringBuilder block = new StringBuilder("layout(std140) uniform OculusLegacyUniforms {\n");
      for (UniformBinding uniform : uniforms) {
         UniformType type = uniform.semantic().type();
         block.append("    ").append(type.glsl()).append(' ').append(uniform.name()).append(";\n");
         // Minecraft's Std140Builder consumes 16 bytes for vec3/ivec3. GLSL
         // otherwise permits the following scalar to occupy their fourth lane.
         if (type == UniformType.VEC3 || type == UniformType.IVEC3) {
            block.append("    float oculus_UniformPadding_").append(uniform.name()).append(";\n");
         }
      }
      return block.append("};\n").toString();
   }

   private static void validateVaryings(String source, Map<String, String> vertexOutputs) throws ShaderCompatibilityException {
      Matcher matcher = VARYING_DECLARATION.matcher(source);

      while (matcher.find()) {
         String type = matcher.group(1);
         String name = matcher.group(2);
         String vertexType = vertexOutputs.get(name);
         if (!type.equals(vertexType)) {
            throw new ShaderCompatibilityException("Fragment varying '" + name + "' (" + type + ") has no matching final.vsh output");
         }
      }
   }

   private static void validateNoUnmappedUniforms(String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers) throws ShaderCompatibilityException {
      String withoutSamplerDeclarations = source;

      for (LegacyFinalShaderAdapter.SamplerBinding sampler : samplers) {
         withoutSamplerDeclarations = withoutSamplerDeclarations.replaceAll(
            "(?m)^[\\t ]*uniform[\\t ]+sampler2D(?:Shadow)?[\\t ]+" + Pattern.quote(sampler.name()) + "[\\t ]*;[\\t ]*(?://.*)?$", ""
         );
      }

      Matcher remaining = ANY_UNIFORM_DECLARATION.matcher(withoutComments(withoutSamplerDeclarations));
      if (remaining.find()) {
         throw new ShaderCompatibilityException("Unsupported uniform declaration in final.fsh: " + remaining.group().trim());
      }
   }

   private static String removeLooseUniforms(String source, List<LegacyFinalShaderAdapter.UniformBinding> uniforms) {
      if (uniforms.isEmpty()) {
         return source;
      } else {
         Set<String> names = uniforms.stream().map(LegacyFinalShaderAdapter.UniformBinding::name).collect(Collectors.toSet());
         Matcher matcher = UNIFORM.matcher(source);
         StringBuilder result = new StringBuilder(source.length());

         while (matcher.find()) {
            if (names.contains(matcher.group(2))) {
               matcher.appendReplacement(result, "");
            }
         }

         matcher.appendTail(result);
         return result.toString();
      }
   }

   private static String adaptDepthSampling(String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers) {
      String adapted = source;

      for (LegacyFinalShaderAdapter.SamplerBinding sampler : samplers) {
         if (sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.DEPTH
             || sampler.texture() == LegacyFinalShaderAdapter.TextureSemantic.SHADOW_DEPTH) {
            String name = Pattern.quote(sampler.name());
            adapted = adapted.replaceAll("\\btexture2DLod\\s*\\(\\s*" + name + "\\s*,", "oculus_textureDepthLod(" + sampler.name() + ",");
            adapted = adapted.replaceAll("\\btexture2D\\s*\\(\\s*" + name + "\\s*,", "oculus_textureDepth(" + sampler.name() + ",");
            adapted = adapted.replaceAll("\\btextureLod\\s*\\(\\s*" + name + "\\s*,", "oculus_textureDepthLod(" + sampler.name() + ",");
            adapted = adapted.replaceAll("\\btexture\\s*\\(\\s*" + name + "\\s*,", "oculus_textureDepth(" + sampler.name() + ",");
            adapted = adapted.replaceAll("\\btexelFetch\\s*\\(\\s*" + name + "\\s*,", "oculus_fetchDepth(" + sampler.name() + ",");
         }
      }

      return adapted;
   }

   /**
    * Texture tracking is bounded. Several legacy names intentionally resolve
    * to the same backing image in this bridge
    * (for example depthtex0/gdepthtex). Distinct depthtex0/1/2 and shadowtex0/1 identities must never merge.
    * Make true aliases share one GLSL
    * sampler as well, avoiding redundant units without changing the data supplied.
    */
   private static CanonicalSamplers canonicalizeSamplers(
      String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers
   ) {
      Map<SamplerKey, LegacyFinalShaderAdapter.SamplerBinding> canonical = new LinkedHashMap<>();
      List<LegacyFinalShaderAdapter.SamplerBinding> unique = new ArrayList<>();
      String adapted = source;
      for (LegacyFinalShaderAdapter.SamplerBinding sampler : samplers) {
         SamplerKey key = new SamplerKey(sampler.texture(), sampler.colorIndex(), sampler.imageKey());
         LegacyFinalShaderAdapter.SamplerBinding existing = canonical.putIfAbsent(key, sampler);
         if (existing == null) {
            unique.add(sampler);
            continue;
         }
         adapted = adapted.replaceAll(
            "(?m)^[\\t ]*uniform[\\t ]+sampler2D[\\t ]+" + Pattern.quote(sampler.name())
               + "[\\t ]*;[\\t ]*(?://.*)?$", ""
         );
         adapted = adapted.replaceAll("\\b" + Pattern.quote(sampler.name()) + "\\b",
            Matcher.quoteReplacement(existing.name()));
      }
      return new CanonicalSamplers(adapted, List.copyOf(unique));
   }

   private static String adaptShadowSampling(String source) {
      Matcher matcher = SAMPLER.matcher(source);
      String adapted = source;
      while (matcher.find()) {
         if (!"sampler2DShadow".equals(matcher.group(1))) continue;
         String name = Pattern.quote(matcher.group(2));
         adapted = adapted.replaceAll("(?m)uniform\\s+sampler2DShadow\\s+" + name + "\\s*;", "uniform sampler2D " + matcher.group(2) + ";");
         adapted = adapted.replaceAll("\\b(?:shadow2D|texture2D|texture)\\s*\\(\\s*" + name + "\\s*,", "oculus_shadowCompare(" + matcher.group(2) + ",");
      }
      return adapted;
   }

   private static String declarations(List<LegacyFinalShaderAdapter.UniformBinding> uniforms,
      java.util.SortedSet<Integer> legacyOutputs, boolean samplesDepth, boolean shadowCompare) {
      StringBuilder result = new StringBuilder();
      for (int index : legacyOutputs) result.append("layout(location=").append(index).append(") out vec4 oculus_FragColor").append(index).append(";\n");

      if (samplesDepth) {
         result.append(
            "vec4 oculus_reverseDepth(vec4 sampleValue) {\n    sampleValue.r = 1.0 - sampleValue.r;\n    return sampleValue;\n}\nvec4 oculus_textureDepth(sampler2D image, vec2 uv) {\n    return oculus_reverseDepth(texture(image, uv));\n}\nvec4 oculus_textureDepth(sampler2D image, vec2 uv, float bias) {\n    return oculus_reverseDepth(texture(image, uv, bias));\n}\nvec4 oculus_textureDepthLod(sampler2D image, vec2 uv, float lod) {\n    return oculus_reverseDepth(textureLod(image, uv, lod));\n}\n"
         );
      }

      if (shadowCompare) {
         result.append("vec4 oculus_shadowCompare(sampler2D image, vec3 coordinate) { float stored = 1.0 - texture(image, coordinate.xy).r; return vec4(step(coordinate.z, stored)); }\n")
            .append("vec4 oculus_shadowCompare(sampler2D image, vec4 coordinate) { return oculus_shadowCompare(image, coordinate.xyz); }\n");
      }

      if (samplesDepth) result.append("vec4 oculus_fetchDepth(sampler2D image, ivec2 uv, int lod) { return oculus_reverseDepth(texelFetch(image, uv, lod)); }\n");

      if (!uniforms.isEmpty()) {
         result.append(uniformBlock(uniforms));
      }

      return result.toString();
   }

   private static String withoutComments(String source) {
      StringBuilder output = new StringBuilder(source.length());
      boolean line = false;
      boolean block = false;

      for (int index = 0; index < source.length(); index++) {
         char current = source.charAt(index);
         char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;
         if (line) {
            output.append(current == '\n' ? '\n' : ' ');
            if (current == '\n') {
               line = false;
            }
         } else if (block) {
            output.append(current == '\n' ? '\n' : ' ');
            if (current == '*' && next == '/') {
               output.append(' ');
               index++;
               block = false;
            }
         } else if (current == '/' && next == '/') {
            output.append("  ");
            index++;
            line = true;
         } else if (current == '/' && next == '*') {
            output.append("  ");
            index++;
            block = true;
         } else {
            output.append(current);
         }
      }

      return output.toString();
   }

   public static record AdaptedShader(
      String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers, List<LegacyFinalShaderAdapter.UniformBinding> uniforms
   ) {
      public AdaptedShader(String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers, List<LegacyFinalShaderAdapter.UniformBinding> uniforms) {
         samplers = List.copyOf(samplers);
         uniforms = List.copyOf(uniforms);
         this.source = source;
         this.samplers = samplers;
         this.uniforms = uniforms;
      }
   }

   public static record SamplerBinding(String name, LegacyFinalShaderAdapter.TextureSemantic texture, int colorIndex, String imageKey) {
      public SamplerBinding(String name, TextureSemantic texture, int colorIndex) { this(name, texture, colorIndex, ""); }
   }

   private record SamplerKey(TextureSemantic texture, int colorIndex, String imageKey) {}

   private record CanonicalSamplers(String source, List<SamplerBinding> samplers) {}

   public static enum TextureSemantic {
      COLOR,
      DEPTH,
      CENTER_DEPTH,
      SHADOW_DEPTH,
      SHADOW_COLOR,
      NOISE,
      CUSTOM;
   }

   public static record UniformBinding(String name, LegacyFinalShaderAdapter.UniformSemantic semantic) {
   }

   public static enum UniformSemantic {
      CUSTOM_FLOAT(LegacyFinalShaderAdapter.UniformType.FLOAT),
      VIEW_WIDTH(LegacyFinalShaderAdapter.UniformType.FLOAT),
      VIEW_HEIGHT(LegacyFinalShaderAdapter.UniformType.FLOAT),
      ASPECT_RATIO(LegacyFinalShaderAdapter.UniformType.FLOAT),
      FRAME_TIME(LegacyFinalShaderAdapter.UniformType.FLOAT),
      FRAME_TIME_SMOOTH(LegacyFinalShaderAdapter.UniformType.FLOAT),
      FRAME_TIME_COUNTER(LegacyFinalShaderAdapter.UniformType.FLOAT),
      FRAME_COUNTER(LegacyFinalShaderAdapter.UniformType.INT),
      WORLD_TIME(LegacyFinalShaderAdapter.UniformType.INT),
      WORLD_DAY(LegacyFinalShaderAdapter.UniformType.INT),
      MOON_PHASE(LegacyFinalShaderAdapter.UniformType.INT),
      RAIN_STRENGTH(LegacyFinalShaderAdapter.UniformType.FLOAT),
      WETNESS(LegacyFinalShaderAdapter.UniformType.FLOAT),
      EYE_ALTITUDE(LegacyFinalShaderAdapter.UniformType.FLOAT),
      NEAR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      FAR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      SUN_ANGLE(LegacyFinalShaderAdapter.UniformType.FLOAT),
      TIME_ANGLE(LegacyFinalShaderAdapter.UniformType.FLOAT),
      SCREEN_BRIGHTNESS(LegacyFinalShaderAdapter.UniformType.FLOAT),
      CLOUD_HEIGHT(LegacyFinalShaderAdapter.UniformType.FLOAT),
      ATLAS_SIZE(LegacyFinalShaderAdapter.UniformType.IVEC2),
      HELD_ITEM_ID(LegacyFinalShaderAdapter.UniformType.INT),
      HELD_ITEM_ID_2(LegacyFinalShaderAdapter.UniformType.INT),
      IS_EYE_IN_WATER(LegacyFinalShaderAdapter.UniformType.INT),
      CAMERA_POSITION(LegacyFinalShaderAdapter.UniformType.VEC3),
      PREVIOUS_CAMERA_POSITION(LegacyFinalShaderAdapter.UniformType.VEC3),
      FOG_COLOR(LegacyFinalShaderAdapter.UniformType.VEC3),
      LEGACY_FOG_COLOR(LegacyFinalShaderAdapter.UniformType.VEC4),
      LEGACY_FOG_START(LegacyFinalShaderAdapter.UniformType.FLOAT),
      LEGACY_FOG_END(LegacyFinalShaderAdapter.UniformType.FLOAT),
      SKY_COLOR(LegacyFinalShaderAdapter.UniformType.VEC3),
      SUN_POSITION(LegacyFinalShaderAdapter.UniformType.VEC3),
      END_FLASH_INTENSITY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      END_FLASH_POSITION(LegacyFinalShaderAdapter.UniformType.VEC3),
      NIGHT_VISION(LegacyFinalShaderAdapter.UniformType.FLOAT),
      BLIND_FACTOR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      DARKNESS_FACTOR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      DARKNESS_LIGHT_FACTOR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      MAX_BLINDNESS_DARKNESS(LegacyFinalShaderAdapter.UniformType.FLOAT),
      SHADOW_FADE(LegacyFinalShaderAdapter.UniformType.FLOAT),
      TIME_BRIGHTNESS(LegacyFinalShaderAdapter.UniformType.FLOAT),
      EYE_BRIGHTNESS(LegacyFinalShaderAdapter.UniformType.IVEC2),
      EYE_BRIGHTNESS_SMOOTH(LegacyFinalShaderAdapter.UniformType.IVEC2),
      EYE_BRIGHTNESS_MAX(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_DRY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_SNOWY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_RAINY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_PALE_GARDEN(LegacyFinalShaderAdapter.UniformType.FLOAT),
      CENTER_DEPTH_SMOOTH(LegacyFinalShaderAdapter.UniformType.FLOAT),
      CAMERA_VELOCITY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_DESERT(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_MESA(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_COLD(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_SWAMP(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_MUSHROOM(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_SAVANNA(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IS_JUNGLE(LegacyFinalShaderAdapter.UniformType.FLOAT),
      CAMERA_POSITION_INT(LegacyFinalShaderAdapter.UniformType.IVEC3),
      CAMERA_POSITION_FRACT(LegacyFinalShaderAdapter.UniformType.VEC3),
      PREVIOUS_CAMERA_POSITION_FRACT(LegacyFinalShaderAdapter.UniformType.VEC3),
      RAIN_FACTOR(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_BASALT_DELTAS(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_CRIMSON_FOREST(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_NETHER_WASTES(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_SOUL_VALLEY(LegacyFinalShaderAdapter.UniformType.FLOAT),
      IN_WARPED_FOREST(LegacyFinalShaderAdapter.UniformType.FLOAT),
      GBUFFER_PROJECTION(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_PROJECTION_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_MODEL_VIEW(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_MODEL_VIEW_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_PREVIOUS_PROJECTION(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_PREVIOUS_PROJECTION_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_PREVIOUS_MODEL_VIEW(LegacyFinalShaderAdapter.UniformType.MAT4),
      GBUFFER_PREVIOUS_MODEL_VIEW_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4),
      SHADOW_PROJECTION(LegacyFinalShaderAdapter.UniformType.MAT4),
      SHADOW_PROJECTION_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4),
      SHADOW_MODEL_VIEW(LegacyFinalShaderAdapter.UniformType.MAT4),
      SHADOW_MODEL_VIEW_INVERSE(LegacyFinalShaderAdapter.UniformType.MAT4);

      private final LegacyFinalShaderAdapter.UniformType type;

      private UniformSemantic(LegacyFinalShaderAdapter.UniformType type) {
         this.type = type;
      }

      public LegacyFinalShaderAdapter.UniformType type() {
         return this.type;
      }
   }

   public static enum UniformType {
      FLOAT("float"),
      INT("int"),
      VEC3("vec3"),
      VEC4("vec4"),
      IVEC3("ivec3"),
      IVEC2("ivec2"),
      MAT4("mat4");

      private final String glsl;

      private UniformType(String glsl) {
         this.glsl = glsl;
      }

      public String glsl() {
         return this.glsl;
      }
   }
}
