package dev.oculusforge.render.program;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Fixed std140 layout for matrices, camera, viewport, world time and shared animation time. */
public final class NativeWorldBindings {
    public static final String BLOCK_NAME = "OculusWorldMatrices";
    // Smoothed ivec2 occupies 608..615, private biome code 616..619; far uses 620..623.
    public static final int CUSTOM_VALUES_OFFSET = 624;
    public static final int FOG_VALUES_OFFSET = CUSTOM_VALUES_OFFSET + CustomUniformBindings.MAX_VALUES * 4;
    public static final int PREVIOUS_CAMERA_OFFSET = FOG_VALUES_OFFSET + 32;
    public static final int CLOUD_HEIGHT_OFFSET = PREVIOUS_CAMERA_OFFSET + 12;
    public static final int ATLAS_SIZE_OFFSET = PREVIOUS_CAMERA_OFFSET + 16;
    public static final int HELD_ITEM_IDS_OFFSET = ATLAS_SIZE_OFFSET + 8;
    public static final int DARKNESS_LIGHT_OFFSET = ATLAS_SIZE_OFFSET + 16;
    public static final int FOG_RANGE_OFFSET = DARKNESS_LIGHT_OFFSET + 4;
    public static final int SKY_COLOR_OFFSET = DARKNESS_LIGHT_OFFSET + 16;
    public static final int BYTE_SIZE = SKY_COLOR_OFFSET + 16;
    public static final int WETNESS_OFFSET = SKY_COLOR_OFFSET + 12;
    private static final Map<String, String> TYPES = types();
    private static final Pattern DECLARATION = Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern LIVE_DECLARATION = Pattern.compile("\\buniform\\s+(\\w+)\\s+(atlasSize|heldItemId2?|darknessLightFactor|fogStart|fogEnd|skyColor|wetness)\\b([^;{}]*);");
    private static final Pattern CLOUD_DECLARATION = Pattern.compile("\\buniform\\s+(\\w+)\\s+cloudHeight\\s*([^;{}]*);");
    private static final Pattern FLOAT_DEFAULT = Pattern.compile("\\s*=\\s*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?)\\s*");
    private static Map<String, String> types() {
        var result = new LinkedHashMap<String, String>();
        for (String name : new String[] {"gbufferProjection", "gbufferProjectionInverse",
            "gbufferModelView", "gbufferModelViewInverse", "shadowProjection", "shadowProjectionInverse",
            "shadowModelView", "shadowModelViewInverse"}) result.put(name, "mat4");
        result.put("cameraPosition", "vec3");
        result.put("viewWidth", "float");
        result.put("viewHeight", "float");
        result.put("aspectRatio", "float");
        result.put("worldTime", "int");
        result.put("worldDay", "int");
        result.put("frameCounter", "int");
        result.put("frameTime", "float");
        result.put("frameTimeCounter", "float");
        result.put("relativeEyePosition", "vec3");
        result.put("heldBlockLightValue", "int");
        result.put("heldBlockLightValue2", "int");
        result.put("moonPhase", "int");
        result.put("rainStrength", "float");
        result.put("nightVision", "float");
        // Uses the former 596..599 alignment gap, preserving every later offset.
        result.put("endFlashIntensity", "float");
        result.put("eyeBrightness", "ivec2");
        result.put("eyeBrightnessSmooth", "ivec2");
        result.put("oculusBiomeCode", "int");
        // Reuse the scalar alignment gap without shifting custom values.
        result.put("far", "float");
        for(int slot=0;slot<CustomUniformBindings.MAX_VALUES;slot++) result.put(CustomUniformBindings.name(slot),"float");
        result.put("fogColor", "vec3");
        result.put("isEyeInWater", "int");
        result.put("darknessFactor", "float");
        // Fill the former scene-block padding without moving earlier offsets.
        result.put("blindness", "float");
        result.put("screenBrightness", "float");
        result.put("previousCameraPosition", "vec3");
        result.put("atlasSize", "ivec2");
        result.put("heldItemId", "int");
        result.put("heldItemId2", "int");
        result.put("darknessLightFactor", "float");
        result.put("fogStart", "float");
        result.put("fogEnd", "float");
        return java.util.Collections.unmodifiableMap(result);
    }
    private NativeWorldBindings() { }
    /** Never turn these live inputs into constants or discard invalid unused declarations. */
    static void validateLiveDeclarations(String source) throws ShaderCompatibilityException {
        var declarations=LIVE_DECLARATION.matcher(source);
        var seen=new HashSet<String>();
        while(declarations.find()) {
            String name=declarations.group(2);
            if(!seen.add(name)||!declarations.group(1).equals(type(name))||!declarations.group(3).isBlank())
                throw new ShaderCompatibilityException("Invalid or duplicate live uniform "+name);
        }
    }
    static boolean supports(String name) { return type(name) != null; }
    static String type(String name) {
        if (name.equals("skyColor")) return "vec3";
        return java.util.Set.of("eyeAltitude", "sunAngle", "maxBlindnessDarkness", "cloudHeight", "wetness").contains(name) ? "float" : TYPES.get(name);
    }
    public static String internal(String name) { return "oculusNative_" + name; }
    /** A uniform initializer is a fallback, never a replacement for the real host binding. */
    static String normalizeCloudHeightDefault(String source) throws ShaderCompatibilityException {
        var declarations=CLOUD_DECLARATION.matcher(source);
        var result=new StringBuilder();int count=0;
        while(declarations.find()) {
            if(++count>1||!declarations.group(1).equals("float"))
                throw new ShaderCompatibilityException("Invalid or duplicate cloudHeight declaration");
            String suffix=declarations.group(2);
            if(suffix.isBlank()) continue;
            var fallback=FLOAT_DEFAULT.matcher(suffix);
            if(!fallback.matches())throw new ShaderCompatibilityException("Unsupported cloudHeight initializer");
            try {
                if(!Float.isFinite(Float.parseFloat(fallback.group(1))))throw new NumberFormatException();
            }catch(NumberFormatException invalid){throw new ShaderCompatibilityException("Nonfinite cloudHeight initializer");}
            String replacement=source.substring(declarations.start(),declarations.start(2))
                +suffix.replaceAll("[^\\r\\n]"," ")+";";
            declarations.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        declarations.appendTail(result);return result.toString();
    }
    public static String block() {
        var result = new StringBuilder("layout(std140) uniform " + BLOCK_NAME + " {\n");
        TYPES.forEach((name, type) -> result.append(type.equals("vec3") ? "vec4" : type)
            .append(' ').append(internal(name)).append(";\n"));
        return result.append("float oculusNative_fogAlpha;\nvec4 oculusNative_skyColor;\n};\n").toString();
    }

    public static Adapted adapt(String source) throws ShaderCompatibilityException {
        var declarations = DECLARATION.matcher(source);
        var found = new HashSet<String>();
        var stripped = new StringBuilder();
        boolean shadows = false;
        while (declarations.find()) {
            String type = declarations.group(1), name = declarations.group(2);
            String expected = type(name);
            if (expected == null) continue;
            if (!expected.equals(type) || !found.add(name))
                throw new ShaderCompatibilityException("Invalid or duplicate native world uniform " + name);
            shadows |= name.startsWith("shadow");
            declarations.appendReplacement(stripped, "");
        }
        declarations.appendTail(stripped);
        String result = stripped.toString();
        // eyeAltitude aliases the same snapshot as cameraPosition.y without growing the UBO.
        for (String name : found) result = NativeUniformReferences.replace(result, name, switch (name) {
            case "eyeAltitude" -> internal("cameraPosition") + ".y";
            case "sunAngle" -> internal("fogColor") + ".w";
            case "cloudHeight" -> internal("previousCameraPosition") + ".w";
            case "skyColor" -> internal("skyColor") + ".xyz";
            case "wetness" -> internal("skyColor") + ".w";
            case "maxBlindnessDarkness" -> "max(" + internal("blindness") + ", " + internal("darknessFactor") + ")";
            default -> internal(name) + (TYPES.get(name).equals("vec3") ? ".xyz" : "");
        }, name.equals("wetness"));
        return new Adapted(result, !found.isEmpty(), shadows);
    }
    public record Adapted(String source, boolean used, boolean needsShadow) { }
}
