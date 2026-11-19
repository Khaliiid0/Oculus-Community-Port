package dev.oculusforge.render.pipeline;

/** A reversible frame-admission gate, not a pipeline failure or a resource owner. */
public final class RenderCompatibilityPause {
    public static final String IMPROVED_TRANSPARENCY =
        "Shaders paused: Improved Transparency is unsupported. Turn it OFF to resume; your pack stays selected.";
    private volatile String reason = "";

    /** Check both the extracted frame and renderer choice: either can forbid admission. */
    public boolean update(boolean pipelinePresent, boolean needsClassicTransparency,
        boolean frameImprovedTransparency, boolean rendererImprovedTransparency) {
        String next = pipelinePresent && needsClassicTransparency
            && (frameImprovedTransparency || rendererImprovedTransparency)
            ? IMPROVED_TRANSPARENCY : "";
        if (next.equals(reason)) return false;
        reason = next;
        return true;
    }

    public boolean paused() { return !reason.isEmpty(); }
    public String reason() { return reason; }
    public <T> T visible(T configuredPipeline) { return paused() ? null : configuredPipeline; }
    public void clear() { reason = ""; }
}
