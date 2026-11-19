package dev.oculusforge.render.program;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Actual pack GLSL with explicit validation against the session's terrain mesh inputs. */
public record NativeTerrainProgram(String vertexSource, String fragmentSource, String fingerprint,
                                   boolean usesWorldMatrices, boolean needsShadowMatrices, List<Integer> outputTargets) {
    public NativeTerrainProgram { outputTargets = List.copyOf(outputTargets); }
    public boolean usesMaterialIds() { return vertexSource.contains("uniform sampler2D OculusMaterialIds;"); }
    public boolean usesNoise() { return fragmentSource.contains("uniform sampler2D OculusNoise;"); }
    public boolean usesSpecular() { return fragmentSource.contains("uniform sampler2D OculusSpecular;"); }
    public boolean usesNormals() { return fragmentSource.contains("uniform sampler2D OculusNormals;"); }
    public List<Integer> colorInputs() { return NativeColorSamplers.inputs(fragmentSource); }
    public boolean usesOpaqueWorldDepth() { return fragmentSource.contains("uniform sampler2D OculusOpaqueWorldDepth;"); }
    public boolean usesShadowColor() { return fragmentSource.contains("uniform sampler2D OculusShadowColor;"); }
    public boolean usesShadowColor1() { return fragmentSource.contains("uniform sampler2D OculusShadowColor1;"); }
    public List<Integer> shadowColorInputs() {
        return java.util.stream.IntStream.range(0, 2)
            .filter(i -> i == 0 ? usesShadowColor() : usesShadowColor1()).boxed().toList();
    }
    public boolean usesShadowDepth() { return fragmentSource.contains("uniform sampler2D OculusShadowDepth;"); }
    public boolean usesOpaqueShadowDepth() { return fragmentSource.contains("uniform sampler2D OculusOpaqueShadowDepth;"); }
    public boolean usesShadowTextures() { return usesShadowColor() || usesShadowColor1() || usesShadowDepth() || usesOpaqueShadowDepth(); }
    public boolean hasShadowProducer(NativeTerrainProgram producer) {
        return !usesShadowTextures() || producer != null && producer.outputTargets().containsAll(shadowColorInputs());
    }

    private static final String MATERIAL_INPUT = """
        in int OculusState;
        uniform sampler2D OculusMaterialIds;
        vec4 oculusNativeMaterial() {
            int index = abs(OculusState);
            ivec2 size = textureSize(OculusMaterialIds, 0);
            if (index < 0 || index >= size.x * size.y) index = 0;
            vec3 bytes = floor(texelFetch(OculusMaterialIds, ivec2(index % size.x, index / size.x), 0).rgb * 255.0 + 0.5);
            float materialId = dot(bytes, vec3(1.0, 256.0, 65536.0)) - 1.0;
            return vec4(materialId, OculusState < 0 ? 1.0 : 0.0, 0.0, 1.0);
        }
        """;
    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#version[^\\n]*");
    private static final Pattern SAMPLER = Pattern.compile("\\buniform\\s+sampler2D\\s+(\\w+)\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern BUILTIN = Pattern.compile("\\bgl_\\w+\\b");
    private static final String VERTEX_INPUTS = dev.oculusforge.port.TerrainShaderAbi.GLOBALS
        + dev.oculusforge.port.TerrainShaderAbi.TERRAIN + dev.oculusforge.port.TerrainShaderAbi.SECTION + """
        layout(std140) uniform Projection { mat4 ProjMat; };
        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;
        vec4 oculusNativeVertex;
        """;

    public static NativeTerrainProgram adapt(String vertex, String fragment, List<Integer> targets)
        throws ShaderCompatibilityException {
        return adaptWithMesh(vertex, fragment, targets, false, TerrainMeshMode.EXTENDED);
    }

    public static NativeTerrainProgram adaptShadow(String vertex, String fragment, List<Integer> targets)
        throws ShaderCompatibilityException {
        return adaptWithMesh(vertex, fragment, targets, true, TerrainMeshMode.EXTENDED);
    }

    static NativeTerrainProgram adaptWithMesh(String vertex, String fragment, List<Integer> targets, boolean shadow, boolean extended)
        throws ShaderCompatibilityException {
        return adaptWithCustomUniforms(vertex, fragment, targets, shadow, extended, NativeCustomUniforms.EMPTY);
    }

    static NativeTerrainProgram adaptWithCustomUniforms(String vertex, String fragment, List<Integer> targets,
        boolean shadow, boolean extended, NativeCustomUniforms custom) throws ShaderCompatibilityException {
        String v = custom.adapt(clean(vertex)), f = custom.adapt(clean(fragment));
        // Preserve the existing depth-write restriction even inside unused helpers.
        if (Pattern.compile("\\b(?:gl_FragDepth|gl_FragDepthEXT)\\b").matcher(f).find()) {
            throw new ShaderCompatibilityException("native terrain fragment-depth conversion is not implemented");
        }
        v = NativeChunkContext.adapt(v);
        f = NativeChunkContext.adapt(f);
        v = NativeUnusedUniforms.adaptReachable(v);
        f = NativeUnusedUniforms.adaptReachable(f);
        var vertexFog=LegacyFogAccess.adapt(v,true);
        var fragmentFog=LegacyFogAccess.adapt(f,true);
        v=vertexFog.source();f=fragmentFog.source();
        if (shadow && (targets.isEmpty() || targets.size() > 2 || targets.stream().distinct().count() != targets.size()
            || targets.stream().anyMatch(i -> i < 0 || i > 1)))
            throw new ShaderCompatibilityException("Native shadow requires distinct shadowcolor0/1 outputs");
        if (shadow) validateShadowFormats(vertex + "\n" + fragment);
        f = NativeShadowDepth.adapt(f, shadow);
        f = NativeOpaqueDepth.adapt(f, shadow);
        var vertexBindings = NativeWorldBindings.adapt(v);
        var fragmentBindings = NativeWorldBindings.adapt(f);
        boolean shadowBuiltins = shadow && Pattern.compile("\\b(?:gl_ProjectionMatrix|gl_ModelViewMatrix|gl_NormalMatrix)\\b").matcher(v).find();
        boolean worldMatrices = vertexBindings.used() || fragmentBindings.used() || shadowBuiltins || vertexFog.used() || fragmentFog.used();
        boolean shadowMatrices = vertexBindings.needsShadow() || fragmentBindings.needsShadow() || shadowBuiltins;
        v = vertexBindings.source();
        f = fragmentBindings.source();
        String extraInputs = "";
        if (extended) {
            // Declaration types matter: do not silently truncate a pack's requested input.
            var attributes = Pattern.compile("(?m)^[\\t ]*attribute[\\t ]+(vec[234])[\\t ]+(mc_midTexCoord|at_tangent|mc_Entity)[\\t ]*;[\\t ]*$").matcher(v);
            Map<String, String> types = new LinkedHashMap<>();
            while (attributes.find()) {
                String type = attributes.group(1), name = attributes.group(2);
                if (types.putIfAbsent(name, type) != null || (name.equals("at_tangent") && !type.equals("vec4"))
                    || (name.equals("mc_midTexCoord") && !List.of("vec2", "vec4").contains(type)))
                    throw new ShaderCompatibilityException("Unsupported extended terrain attribute declaration " + name);
            }
            v = attributes.replaceAll("");
            for (String name : List.of("mc_midTexCoord", "at_tangent", "mc_Entity")) {
                if (Pattern.compile("\\b" + name + "\\b").matcher(v).find() && !types.containsKey(name))
                    throw new ShaderCompatibilityException("Missing extended terrain attribute declaration " + name);
            }
            v = v.replaceAll("\\bgl_Normal\\b", "OculusNormal.xyz")
                .replaceAll("\\bgl_NormalMatrix\\b", "transpose(inverse(mat3(" +
                    (shadow ? NativeWorldBindings.internal("shadowModelView") : "ModelViewMat") + ")))")
                .replaceAll("\\bat_tangent\\b", "OculusTangent")
                .replaceAll("\\bmc_midTexCoord\\b", "vec2".equals(types.get("mc_midTexCoord")) ? "OculusMidUV" : "vec4(OculusMidUV, 0.0, 1.0)");
            extraInputs = "in vec4 OculusNormal;\nin vec4 OculusTangent;\nin vec2 OculusMidUV;\n";
            if (types.containsKey("mc_Entity")) {
                extraInputs += MATERIAL_INPUT;
                String components = switch (types.get("mc_Entity")) { case "vec2" -> ".xy"; case "vec3" -> ".xyz"; default -> ""; };
                v = v.replaceAll("\\bmc_Entity\\b", "oculusNativeMaterial()" + components);
            }
        }
        if (Pattern.compile("\\b(?:attribute|gl_Normal|gl_NormalMatrix|mc_Entity|mc_midTexCoord|at_tangent)\\b")
            .matcher(v).find()) {
            throw new ShaderCompatibilityException(extended ? "terrain requires mesh attributes not yet bound"
                : "terrain requires mesh attributes (normal/tangent/material/mid-UV) absent from Minecraft BLOCK vertices");
        }
        var vertexInterface = NativeStageInterface.parse(v, true);
        var fragmentInterface = NativeStageInterface.parse(f, false);
        NativeStageInterface.match(vertexInterface, fragmentInterface);
        if (Pattern.compile("\\b(?:uniform|in|out|inout|varying|layout)\\b").matcher(vertexInterface.validationSource()).find()) {
            throw new ShaderCompatibilityException("terrain vertex uniforms or custom input declarations need the extended world binding path"
                + unboundDeclarations(vertexInterface.validationSource(), null));
        }
        Map<String, String> outputs = vertexInterface.types();
        v = vertexInterface.source();
        f = fragmentInterface.source();
        // These are actual mesh inputs, not fabricated normals, IDs or fullscreen positions.
        String combined = shadow ? "ModelViewMat" : "ProjMat * ModelViewMat";
        v = v.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(" + combined + " * oculusNativeVertex)");
        v = v.replaceAll("\\bgl_ModelViewProjectionMatrix\\b", "(" + combined + ")");
        v = v.replaceAll("\\bgl_ModelViewMatrix\\b", shadow ? NativeWorldBindings.internal("shadowModelView") : "ModelViewMat");
        v = v.replaceAll("\\bgl_ProjectionMatrix\\b", shadow ? NativeWorldBindings.internal("shadowProjection") : "ProjMat");
        v = v.replaceAll("\\bgl_Vertex\\b", "oculusNativeVertex");
        v = v.replaceAll("\\bgl_Color\\b", "Color");
        v = v.replaceAll("\\bgl_MultiTexCoord0\\b", "vec4(UV0, 0.0, 1.0)");
        v = v.replaceAll("\\bgl_MultiTexCoord1\\b", "vec4(vec2(UV2), 0.0, 1.0)");
        v = v.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]", "mat4(1.0)");
        v = v.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*]",
            "mat4(1.0/256.0,0,0,0, 0,1.0/256.0,0,0, 0,0,1.0/256.0,0, 0.03125,0.03125,0.03125,1)");
        var builtin = BUILTIN.matcher(v);
        while (builtin.find()) if (!builtin.group().equals("gl_Position"))
            throw new ShaderCompatibilityException("Unsupported native terrain vertex built-in " + builtin.group());
        var main = MAIN.matcher(v);
        if (!main.find()) throw new ShaderCompatibilityException("Terrain vertex shader has no main function");
        v = main.replaceFirst("void oculusNativeMain()");
        // Pack expressions/varyings use legacy projection in both stages. Convert
        // only the final position for the native reverse-depth rasterizer.
        var projection = NativeProjection.adaptExpressions(v, !shadow, "Vertex");
        v = "#version 330 core\n" + VERTEX_INPUTS + extraInputs + (worldMatrices ? NativeWorldBindings.block() : "")
            + projection.helpers() + projection.source() + """

            void main() {
                oculusNativeVertex = vec4(Position + vec3(ChunkPosition - CameraBlockPos) + CameraOffset, 1.0);
                oculusNativeMain();
            %s
            }
            """.formatted(shadow ? ShadowClipSpace.CONVERSION : NativeProjection.CLIP_CONVERSION);

        // Normalize inline sampler declarations before aliases and texture2D -> texture.
        // Otherwise a legacy sampler named texture shadows the modern texture() function.
        f = SAMPLER.matcher(f).replaceAll("\nuniform sampler2D $1;\n");

        // Validate color declarations before the fullscreen adapter can remove unused inputs.
        var colorDeclarations = Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*([^;]*);").matcher(f);
        while (colorDeclarations.find()) if (NativeColorSamplers.index(colorDeclarations.group(2)) >= 0
            && (!colorDeclarations.group(1).equals("sampler2D") || !colorDeclarations.group(3).isBlank()))
            throw new ShaderCompatibilityException("Invalid native color sampler declaration " + colorDeclarations.group(2));
        // Auxiliary world images may be sampled only when absent from this pass's outputs.
        var samplers = SAMPLER.matcher(f);
        Map<String, String> rename = new LinkedHashMap<>();
        while (samplers.find()) {
            String name = samplers.group(1);
            String canonical = switch (name) {
                case "texture", "tex", "gtexture" -> "Sampler0";
                case "lightmap" -> "Sampler2";
                case "noisetex" -> "OculusNoise";
                case "specular" -> "OculusSpecular";
                case "normals" -> "OculusNormals";
                case "OculusShadowDepth" -> "OculusShadowDepth";
                case "OculusOpaqueShadowDepth" -> "OculusOpaqueShadowDepth";
                case "OculusOpaqueWorldDepth" -> "OculusOpaqueWorldDepth";
                case "shadowcolor0", "shadowcolor1" -> {
                    if (shadow) throw new ShaderCompatibilityException("Native shadow cannot sample its own " + name + " output");
                    yield name.equals("shadowcolor0") ? "OculusShadowColor" : "OculusShadowColor1";
                }
                default -> NativeColorSamplers.validate(name, targets, shadow);
            };
            if (rename.containsValue(canonical)) throw new ShaderCompatibilityException("Duplicate native terrain texture aliases");
            rename.put(name, canonical);
        }
        for (var entry : rename.entrySet()) {
            // Avoid replacing the GLSL texture(...) function when the legacy sampler is named texture.
            f = f.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b(?!\\s*\\()", entry.getValue());
        }
        var textureBindings = new LinkedHashMap<>(Map.of("Sampler0", "native-atlas", "Sampler2", "native-lightmap", "OculusNoise", "native-noise",
                "OculusShadowColor", "native-shadow-color", "OculusShadowDepth", "native-shadow-depth",
                "OculusOpaqueShadowDepth", "native-opaque-shadow-depth"));
        for(int index=1;index<16;index++) textureBindings.put(NativeColorSamplers.canonical(index),"native-color-"+index);
        textureBindings.put("OculusOpaqueWorldDepth","native-opaque-world-depth");
        textureBindings.put("OculusShadowColor1", "native-shadow-color-1");
        textureBindings.put("OculusSpecular", "native-specular-atlas");
        textureBindings.put("OculusNormals", "native-normal-atlas");
        var adapted = new LegacyFinalShaderAdapter().adapt(f, outputs, textureBindings);
        if (!adapted.uniforms().isEmpty()) throw new ShaderCompatibilityException("Native terrain fragment uniforms need the extended world binding path"
            + unboundDeclarations(f, adapted.uniforms().stream().map(LegacyFinalShaderAdapter.UniformBinding::name)
                .collect(java.util.stream.Collectors.toSet())));
        if (adapted.samplers().stream().anyMatch(s -> s.texture() != LegacyFinalShaderAdapter.TextureSemantic.CUSTOM))
            throw new ShaderCompatibilityException("Native terrain has unbound texture inputs; active world-output feedback remains forbidden: "
                + adapted.samplers().stream().filter(s -> s.texture() != LegacyFinalShaderAdapter.TextureSemantic.CUSTOM)
                    .limit(8).map(s -> s.name()+" ["+s.texture()+"]").collect(java.util.stream.Collectors.joining(", ")));
        f = NativeFragmentDepth.adapt(NativeAlphaTest.adapt(adapted.source()));
        if (worldMatrices) f = f.replaceFirst("(?m)^#version[^\\n]*",
            java.util.regex.Matcher.quoteReplacement("#version 330 core\n" + NativeWorldBindings.block()));
        var locations = Pattern.compile("layout\\(location\\s*=\\s*(\\d+)\\)").matcher(f);
        var writtenLocations = new java.util.HashSet<Integer>();
        while (locations.find()) {
            int location = Integer.parseInt(locations.group(1));
            if (location >= targets.size())
                throw new ShaderCompatibilityException("Native terrain output is outside its draw-buffer layout");
            writtenLocations.add(location);
        }
        if (shadow && writtenLocations.size() != targets.size())
            throw new ShaderCompatibilityException("Native shadow must declare every requested color output");
        // Unknown compatibility built-ins must not sneak into driver compilation.
        builtin = BUILTIN.matcher(f);
        while (builtin.find()) if (!List.of("gl_FragCoord", "gl_FrontFacing").contains(builtin.group()))
            throw new ShaderCompatibilityException("Unsupported native terrain fragment built-in " + builtin.group());
        var program = new NativeTerrainProgram(v, f, hash(v + '\0' + f + '\0' + targets), worldMatrices, shadowMatrices, targets);
        TextureBindingCapacity.validateNative(program);
        return program;
    }

    /** The current shadow attachments are RGBA8; never silently downgrade HDR metadata. */
    private static void validateShadowFormats(String source) throws ShaderCompatibilityException {
        var metadata = Pattern.compile("(?m)^[\\t ]*const\\s+int\\s+shadowcolor([0-9]+)Format\\s*=\\s*([^;\\r\\n]+);").matcher(source);
        while (metadata.find()) if (!metadata.group(1).matches("[01]") || !metadata.group(2).trim().equals("RGBA8"))
            throw new ShaderCompatibilityException("Native shadow currently supports only RGBA8 shadowcolor0/1 formats");
    }

    /** Bounded diagnostics only; this does not loosen declaration validation. */
    private static String unboundDeclarations(String source, java.util.Set<String> activeNames) {
        var declarations = Pattern.compile("\\b(?:uniform|in)\\s+(\\w+)\\s+(\\w+)").matcher(source);
        var names = new java.util.LinkedHashSet<String>();
        boolean truncated = false;
        while (declarations.find()) {
            String name = declarations.group(2);
            if (activeNames != null && !activeNames.contains(name)) continue;
            // Canonical samplers are already bound, not missing world uniforms.
            if (name.equals("Sampler0") || name.equals("Sampler2") || name.equals("OculusNoise")) continue;
            if (names.size() == 12) { truncated = true; break; }
            names.add(name.length() > 64 ? name.substring(0, 64) + "..." : name);
        }
        return names.isEmpty() ? "" : ": " + String.join(", ", names) + (truncated ? ", ..." : "");
    }

    private static String clean(String source) throws ShaderCompatibilityException {
        if (source == null || source.isBlank()) throw new ShaderCompatibilityException("Native terrain requires both vertex and fragment sources");
        String result = PostProcessCapabilities.withoutComments(source.replace("\r\n", "\n").replace('\r', '\n'));
        if (Pattern.compile("\\b" + NativeFragmentDepth.ZERO_TO_ONE_DEFINE + "\\b").matcher(result).find())
            throw new ShaderCompatibilityException("Reserved Oculus fragment depth define");
        if (Pattern.compile("\\b" + ShadowClipSpace.ZERO_TO_ONE_DEFINE + "\\b").matcher(result).find())
            throw new ShaderCompatibilityException("Reserved Oculus shadow depth define");
        if (Pattern.compile("\\b(?:OCULUS_NATIVE_ALPHA_MODE|OCULUS_NATIVE_ALPHA_REF)\\b").matcher(result).find())
            throw new ShaderCompatibilityException("Reserved Oculus alpha-test define");
        if (Pattern.compile("\\b(?:oculusNative|oculusCustomValue)\\w*\\b").matcher(result).find())
            throw new ShaderCompatibilityException("Reserved Oculus native terrain identifier");
        if (Pattern.compile("\\bOculus(?:Normals?|Tangent|MidUV|State|MaterialIds|WorldMatrices|Noise|Specular|ShadowColor1?|ShadowDepth|OpaqueShadowDepth|OpaqueWorldDepth|Color[0-9]+)\\b").matcher(result).find())
            throw new ShaderCompatibilityException("Reserved Oculus mesh input identifier");
        result = NativeShaderExtensions.adapt(result);
        result = result.replaceAll("(?m)^[\\t ]*const\\s+int\\s+shadowcolor[01]Format\\s*=\\s*RGBA8\\s*;", "");
        return NativeWorldBindings.normalizeCloudHeightDefault(NativeShaderDeclarations.expandLists(
            ColorBufferFormats.stripFormatDeclarations(VERSION.matcher(result).replaceAll(""))));
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
