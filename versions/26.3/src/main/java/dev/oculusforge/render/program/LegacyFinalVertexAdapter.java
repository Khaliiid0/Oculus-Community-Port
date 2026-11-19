package dev.oculusforge.render.program;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyFinalVertexAdapter {
   private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#[\\t ]*version[\\t ]+\\d+(?:[\\t ]+\\w+)?[\\t ]*(?://.*)?$");
   private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
   private static final Pattern VARYING = Pattern.compile("(?m)^[\\t ]*varying[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*;[\\t ]*(?://.*)?$");
   private static final Pattern UNIFORM = Pattern.compile("(?m)^[\\t ]*uniform\\b[^\\n;{]*(?:;|\\{)");
   private static final Pattern SIMPLE_UNIFORM = Pattern.compile(
      "(?m)^[\\t ]*uniform[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*;[\\t ]*(?://.*)?$"
   );
   private static final Pattern UNIFORM_LIST = Pattern.compile(
      "(?m)^[\\t ]*uniform[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]+([A-Za-z_][A-Za-z0-9_]*(?:[\\t ]*,[\\t ]*[A-Za-z_][A-Za-z0-9_]*)+)[\\t ]*;[\\t ]*(?://.*)?$"
   );
   private static final Pattern DEFAULTED_UNIFORM = Pattern.compile(
      "(?m)^[\\t ]*uniform[\\t ]+(bool|float|int|vec[234]|ivec[234]|mat4)[\\t ]+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*=[\\t ]*([^;\\n]+);"
   );
   private static final Pattern ATTRIBUTE = Pattern.compile("(?m)^[\\t ]*attribute\\b");
   private static final Pattern LEGACY_BUILTIN = Pattern.compile("\\bgl_([A-Za-z_][A-Za-z0-9_]*)");
   public static final String DEFAULT_SOURCE = "#version 330 core\nout vec2 texcoord;\nout vec2 texCoord;\nvoid main() {\n    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);\n    texcoord = uv;\n    texCoord = uv;\n}\n";
   public static final Map<String, String> DEFAULT_OUTPUTS = Map.of("texcoord", "vec2", "texCoord", "vec2");

   public LegacyFinalVertexAdapter.AdaptedVertex adapt(String input) throws ShaderCompatibilityException {
      return adapt(input, Map.of());
   }

   public LegacyFinalVertexAdapter.AdaptedVertex adapt(String input, Map<String, String> customTextures) throws ShaderCompatibilityException {
      if (input != null && !input.isBlank()) {
         String source = input.replace("\r\n", "\n").replace('\r', '\n');
         if (!source.isEmpty() && source.charAt(0) == '\ufeff') {
            source = source.substring(1);
         }

         source = withoutComments(source);
         source = NativeWorldBindings.normalizeCloudHeightDefault(source);
         source = ColorBufferFormats.stripFormatDeclarations(source);
         source = normalizeUniformLists(source);
         source = CenterDepthInput.adapt(source);
         NativeWorldBindings.validateLiveDeclarations(source);
         source = DEFAULTED_UNIFORM.matcher(source).replaceAll("const $1 $2 = $3;");
         source = stripUnusedUniforms(source);
         var fog=LegacyFogAccess.adapt(source,false);
         source=fog.source();
         String inspected = source;
         if (ATTRIBUTE.matcher(inspected).find()) {
            throw new ShaderCompatibilityException("final.vsh declares custom attributes, but the Forge fullscreen pass has no vertex buffer");
         } else {
            List<LegacyFinalShaderAdapter.UniformBinding> uniforms = parseUniforms(source);
            if(fog.used()) {
               var enriched=new ArrayList<>(uniforms);enriched.addAll(LegacyFogAccess.POST_BINDINGS);uniforms=List.copyOf(enriched);
            }
            List<LegacyFinalShaderAdapter.SamplerBinding> samplers = parseSamplers(source, customTextures);
            source = SIMPLE_UNIFORM.matcher(source).replaceAll("");
            if (UNIFORM.matcher(source).find()) {
               throw new ShaderCompatibilityException("final.vsh contains an unsupported uniform block, array or sampler declaration");
            }
            LinkedHashMap<String, String> outputs = new LinkedHashMap<>();
            Matcher varying = VARYING.matcher(source);

            while (varying.find()) {
               outputs.putIfAbsent(varying.group(2), varying.group(1));
            }

            source = source.replaceAll("\\bvarying\\b", "out");
            source = source.replaceAll("\\btexture2DLod\\b", "textureLod").replaceAll("\\btexture2D\\b", "texture");
            source = source.replaceAll("\\bftransform\\s*\\(\\s*\\)", "oculus_position");
            source = source.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*\\d+\\s*]", "mat4(1.0)");
            source = source.replaceAll("\\bgl_MultiTexCoord0\\b", "vec4(oculus_uv, 0.0, 1.0)");
            source = source.replaceAll("\\bgl_MultiTexCoord[1-7]\\b", "vec4(oculus_uv, 0.0, 1.0)");
            source = source.replaceAll("\\bgl_Vertex\\b", "oculus_position");
            Matcher builtin = LEGACY_BUILTIN.matcher(withoutComments(source));

            while (builtin.find()) {
               if (!"Position".equals(builtin.group(1))) {
                  throw new ShaderCompatibilityException("Unsupported fixed-function vertex built-in gl_" + builtin.group(1));
               }
            }

            Matcher main = MAIN.matcher(source);
            if (!main.find()) {
               throw new ShaderCompatibilityException("final.vsh has no main function");
            } else {
               source = main.replaceFirst("void oculus_pack_main()");
               StringBuilder bridge = new StringBuilder("vec2 oculus_uv;\nvec4 oculus_position;\n");
               for (LegacyFinalShaderAdapter.SamplerBinding sampler : samplers) {
                  bridge.append("uniform sampler2D ").append(sampler.name()).append(";\n");
               }
               String bridgeDeclarations = bridge.toString();
               Matcher version = VERSION.matcher(source);
               if (version.find()) {
                  source = version.replaceFirst(Matcher.quoteReplacement("#version 330 core\n" + bridgeDeclarations));
               } else {
                  source = "#version 330 core\n" + bridgeDeclarations + source;
               }

               source = source
                  + "\nvoid main() {\n    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n    oculus_uv = uv;\n    oculus_position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);\n    oculus_pack_main();\n}\n";
               var active = GlslFunctionReachability.activeSamplers(source, samplers);
               return new LegacyFinalVertexAdapter.AdaptedVertex(active.source(), outputs, uniforms, active.samplers());
            }
         }
      } else {
         throw new ShaderCompatibilityException("final.vsh is empty");
      }
   }

   private static String normalizeUniformLists(String source) {
      Matcher matcher = UNIFORM_LIST.matcher(source);
      StringBuilder result = new StringBuilder(source.length());
      while (matcher.find()) {
         StringBuilder replacement = new StringBuilder();
         for (String name : matcher.group(2).split("\\s*,\\s*")) replacement.append("uniform ").append(matcher.group(1)).append(' ').append(name).append(";\n");
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
      }
      matcher.appendTail(result);
      return result.toString();
   }

   private static String stripUnusedUniforms(String source) {
      String codeOnly = SIMPLE_UNIFORM.matcher(source).replaceAll("");
      Matcher matcher = SIMPLE_UNIFORM.matcher(source);
      StringBuilder result = new StringBuilder(source.length());
      while (matcher.find()) {
         String name = matcher.group(2);
         if (!Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(codeOnly).find()) matcher.appendReplacement(result, "");
      }
      matcher.appendTail(result);
      return result.toString();
   }

   private static List<LegacyFinalShaderAdapter.UniformBinding> parseUniforms(String source) throws ShaderCompatibilityException {
      List<LegacyFinalShaderAdapter.UniformBinding> result = new ArrayList<>();
      Matcher matcher = SIMPLE_UNIFORM.matcher(source);
      while (matcher.find()) {
         String type = matcher.group(1);
         String name = matcher.group(2);
         if (type.startsWith("sampler")) continue;
         LegacyFinalShaderAdapter.UniformSemantic semantic = LegacyFinalShaderAdapter.uniformSemantic(name);
         if (semantic == null || !semantic.type().glsl().equals(type)) {
            throw new ShaderCompatibilityException("Unsupported fullscreen vertex uniform " + type + " " + name);
         }
         result.add(new LegacyFinalShaderAdapter.UniformBinding(name, semantic));
      }
      return List.copyOf(result);
   }

   private static List<LegacyFinalShaderAdapter.SamplerBinding> parseSamplers(String source, Map<String, String> customTextures)
      throws ShaderCompatibilityException {
      List<LegacyFinalShaderAdapter.SamplerBinding> result = new ArrayList<>();
      Matcher matcher = SIMPLE_UNIFORM.matcher(source);
      while (matcher.find()) {
         String type = matcher.group(1);
         if (!type.startsWith("sampler")) continue;
         LegacyFinalShaderAdapter.SamplerBinding binding = LegacyFinalShaderAdapter.samplerBinding(type, matcher.group(2), customTextures);
         if (binding == null) throw new ShaderCompatibilityException("Unsupported fullscreen vertex sampler " + matcher.group(2));
         result.add(binding);
      }
      return List.copyOf(result);
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

   public static record AdaptedVertex(String source, Map<String, String> outputs,
                                      List<LegacyFinalShaderAdapter.UniformBinding> uniforms,
                                      List<LegacyFinalShaderAdapter.SamplerBinding> samplers) {
      public AdaptedVertex(String source, Map<String, String> outputs) {
         this(source, outputs, List.of(), List.of());
      }

      public AdaptedVertex {
         outputs = Map.copyOf(outputs);
         uniforms = List.copyOf(uniforms);
         samplers = List.copyOf(samplers);
      }
   }
}
