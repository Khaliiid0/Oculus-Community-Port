package dev.oculusforge.render.program;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.OculusForge;
import org.lwjgl.opengl.GL33C;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** CPU admission bound plus verified OpenGL tracking and device limits at activation. */
public final class TextureBindingCapacity {
    public static final int MAX_BINDINGS = 16;
    private static int trackedBindings = 12;
    private static boolean logged;
    private static final Pattern NATIVE_SAMPLER = Pattern.compile("\\buniform\\s+sampler2D\\s+(\\w+)\\s*;");

    private TextureBindingCapacity() { }

    record NativeCounts(int distinct, int fragment, int vertex) { }

    /** The terrain snippet reserves both base slots even if GLSL optimizes them out. */
    static NativeCounts nativeCounts(NativeTerrainProgram program) {
        Set<String> fragment = nativeSamplers(program.fragmentSource());
        Set<String> vertex = nativeSamplers(program.vertexSource());
        Set<String> bindings = new HashSet<>(Set.of("Sampler0", "Sampler2"));
        bindings.addAll(fragment);
        bindings.addAll(vertex);
        return new NativeCounts(bindings.size(), fragment.size(), vertex.size());
    }

    private static Set<String> nativeSamplers(String source) {
        Set<String> names = new HashSet<>();
        var matcher = NATIVE_SAMPLER.matcher(PostProcessCapabilities.withoutComments(source));
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    /** Pure CPU admission, before descriptors, texture uploads or driver compilation. */
    static void validateNative(NativeTerrainProgram program) throws ShaderCompatibilityException {
        var counts = nativeCounts(program);
        // Minimum GL 3.3 stage/combined limits; activation also checks the actual device.
        validate(counts.distinct(), counts.fragment(), counts.vertex(), MAX_BINDINGS, 16, 16, 48);
    }

    public static void requireNativeSupported(List<NativeTerrainProgram> programs) throws ShaderCompatibilityException {
        if (programs.isEmpty()) return;
        var limits = deviceLimits();
        for (var program : programs) {
            var counts = nativeCounts(program);
            validate(counts.distinct(), counts.fragment(), counts.vertex(),
                trackedBindings, limits.fragment(), limits.vertex(), limits.combined());
        }
    }

    /** Called only by the GlStateManager static-initializer mixin. */
    public static int extendTracking(int original) {
        if (original != 12) throw new IllegalStateException("Unexpected Minecraft texture tracking capacity: " + original);
        trackedBindings = MAX_BINDINGS;
        return MAX_BINDINGS;
    }

    static void validate(int distinct, int fragment, int vertex, int tracked, int maxFragment, int maxVertex, int maxCombined)
        throws ShaderCompatibilityException {
        if (distinct > MAX_BINDINGS || distinct > tracked || distinct > maxCombined
            || fragment > maxFragment || vertex > maxVertex || (long) fragment + vertex > maxCombined)
            throw new ShaderCompatibilityException("Texture binding capacity exceeded: distinct=" + distinct
                + ", fragment=" + fragment + ", vertex=" + vertex + "; tracked=" + tracked
                + ", device(fragment/vertex/combined)=" + maxFragment + "/" + maxVertex + "/" + maxCombined);
    }

    static void requireSupported(List<PreparedFinalProgram> programs) throws ShaderCompatibilityException {
        var limits = deviceLimits();
        for (var program : programs) {
            int fragmentCount = stageCount(program.fragment().source(), program.fragment().samplers());
            int vertexCount = stageCount(program.vertexSource(), program.fragment().samplers());
            try {
                validate(program.fragment().samplers().size(), fragmentCount, vertexCount,
                    trackedBindings, limits.fragment(), limits.vertex(), limits.combined());
            } catch (ShaderCompatibilityException failure) {
                throw new ShaderCompatibilityException(program.stageName() + ": " + failure.getMessage());
            }
        }
    }

    private record DeviceLimits(int fragment, int vertex, int combined) { }

    private static DeviceLimits deviceLimits() throws ShaderCompatibilityException {
        RenderSystem.assertOnRenderThread();
        if (dev.oculusforge.render.GraphicsApi.fromBackendName(RenderSystem.getDevice().getDeviceInfo().backendName())
            != dev.oculusforge.render.GraphicsApi.OPENGL)
            throw new ShaderCompatibilityException("Texture binding bridge requires the OpenGL backend");
        // This also forces GlStateManager's initialization, including the tracking extension.
        int fragment = GlStateManager._getInteger(GL33C.GL_MAX_TEXTURE_IMAGE_UNITS);
        int vertex = GlStateManager._getInteger(GL33C.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
        int combined = GlStateManager._getInteger(GL33C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        if (!logged) {
            OculusForge.LOGGER.info("Oculus texture binding capacity: tracked={}, fragment={}, vertex={}, combined={}",
                trackedBindings, fragment, vertex, combined);
            logged = true;
        }
        return new DeviceLimits(fragment, vertex, combined);
    }

    private static int stageCount(String source, List<LegacyFinalShaderAdapter.SamplerBinding> samplers) {
        int count = 0;
        for (var sampler : samplers)
            if (Pattern.compile("\\buniform\\s+sampler2D\\s+" + Pattern.quote(sampler.name()) + "\\s*;").matcher(source).find()) count++;
        return count;
    }
}
