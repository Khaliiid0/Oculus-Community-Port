package dev.oculusforge.render.program;

import java.util.List;
import java.util.Map;
import dev.oculusforge.render.world.WorldRenderPipeline;

public final class PackShaderPipeline implements AutoCloseable {
    private final String packId;
    private final Map<String, Variant> variants;
    private final PackGpuTexture noise;
    private final Map<String, PackGpuTexture> images;
    private final WorldRenderPipeline world;
    private boolean closed;

    PackShaderPipeline(String packId, Map<String, Variant> variants) {
        this(packId, variants, null, Map.of(), null);
    }

    PackShaderPipeline(String packId, Map<String, Variant> variants, PackGpuTexture noise, Map<String, PackGpuTexture> images) {
        this(packId, variants, noise, images, null);
    }

    PackShaderPipeline(String packId, Map<String, Variant> variants, PackGpuTexture noise,
                       Map<String, PackGpuTexture> images, WorldRenderPipeline world) {
        this.packId = packId;
        this.variants = Map.copyOf(variants);
        this.noise = noise;
        this.images = Map.copyOf(images);
        this.world = world;
    }

    public String packId() {
        return packId;
    }

    public Variant variant(String programRoot) {
        return variants.getOrDefault(programRoot, variants.get("shaders"));
    }

    public WorldRenderPipeline world() { return world; }

    public void resetTemporalHistory() {
        for (Variant variant : variants.values()) {
            variant.eyeBrightness().reset();
            variant.customUniforms().reset();
            variant.cameraHistory().reset();
            for (PackFinalProgram program : variant.programs()) program.resetTemporalHistory();
        }
    }

    public com.mojang.blaze3d.textures.GpuTextureView noiseView() {
        if (noise == null) throw new IllegalStateException("The pack has no generated noisetex");
        return noise.view();
    }

    public PackGpuTexture image(String key) {
        if (closed) throw new IllegalStateException("Shader pipeline is closed");
        return java.util.Objects.requireNonNull(images.get(key), "Missing custom texture: " + key);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        resetTemporalHistory();
        RuntimeException failure = null;
        for (Variant variant : variants.values()) {
            for (PackFinalProgram program : variant.programs()) {
                try {
                    program.close();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        }
        if (noise != null) {
            try { noise.close(); } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        for (PackGpuTexture image : images.values()) {
            try { image.close(); } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (world != null) {
            try { world.close(); } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public record Variant(List<PackFinalProgram> programs, ColorBufferPlan buffers, EyeBrightnessSmoother eyeBrightness,
                          CustomUniformBindings.State customUniforms, CameraHistory cameraHistory) {
        public Variant(List<PackFinalProgram> programs,ColorBufferPlan buffers,EyeBrightnessSmoother eyeBrightness,
                       CustomUniformBindings.State customUniforms) {
            this(programs,buffers,eyeBrightness,customUniforms,new CameraHistory());
        }
        public Variant(List<PackFinalProgram> programs,ColorBufferPlan buffers,EyeBrightnessSmoother eyeBrightness) {
            this(programs,buffers,eyeBrightness,CustomUniformBindings.EMPTY.newState());
        }
        public Variant(List<PackFinalProgram> programs, ColorBufferPlan buffers) {
            this(programs, buffers, new EyeBrightnessSmoother(EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS));
        }
        public Variant {
            java.util.Objects.requireNonNull(cameraHistory);
            java.util.Objects.requireNonNull(customUniforms);
            java.util.Objects.requireNonNull(eyeBrightness);
            programs = List.copyOf(programs);
        }
    }
}
