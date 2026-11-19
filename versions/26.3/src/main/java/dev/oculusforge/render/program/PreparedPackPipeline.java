package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import java.util.List;
import java.util.Map;

public record PreparedPackPipeline(ShaderPackDescriptor pack, Map<String, Variant> variants, NoiseTextureData noise,
                                   Map<String, PackImageData> images, WorldProgramPlan world, BlockMaterialMap materials,
                                   String worldNoiseImageKey, ItemMaterialMap items, MaterialFormat materialFormat,
                                   Map<String,String> optionValues) {
    public PreparedPackPipeline(ShaderPackDescriptor pack,Map<String,Variant> variants,NoiseTextureData noise,
                                Map<String,PackImageData> images,WorldProgramPlan world,BlockMaterialMap materials,
                                String worldNoiseImageKey,ItemMaterialMap items,MaterialFormat materialFormat) {
        this(pack,variants,noise,images,world,materials,worldNoiseImageKey,items,materialFormat,Map.of());
    }
    public PreparedPackPipeline(ShaderPackDescriptor pack,Map<String,Variant> variants,NoiseTextureData noise,
                                Map<String,PackImageData> images,WorldProgramPlan world,BlockMaterialMap materials,
                                String worldNoiseImageKey,ItemMaterialMap items) {
        this(pack,variants,noise,images,world,materials,worldNoiseImageKey,items,MaterialFormat.DEFAULT);
    }
    public PreparedPackPipeline(ShaderPackDescriptor pack,Map<String,Variant> variants,NoiseTextureData noise,
                                Map<String,PackImageData> images,WorldProgramPlan world,BlockMaterialMap materials,
                                String worldNoiseImageKey) {
        this(pack,variants,noise,images,world,materials,worldNoiseImageKey,ItemMaterialMap.EMPTY);
    }
    public PreparedPackPipeline(ShaderPackDescriptor pack, Map<String, Variant> variants, NoiseTextureData noise,
                                Map<String, PackImageData> images, WorldProgramPlan world, BlockMaterialMap materials) {
        this(pack, variants, noise, images, world, materials, null);
    }
    public PreparedPackPipeline(ShaderPackDescriptor pack, Map<String, Variant> variants, NoiseTextureData noise,
                                Map<String, PackImageData> images, WorldProgramPlan world) {
        this(pack, variants, noise, images, world, BlockMaterialMap.EMPTY);
    }
    public PreparedPackPipeline(ShaderPackDescriptor pack, Map<String, Variant> variants, NoiseTextureData noise) {
        this(pack, variants, noise, Map.of(), new WorldProgramPlan(Map.of()));
    }

    public PreparedPackPipeline(ShaderPackDescriptor pack, Map<String, Variant> variants, NoiseTextureData noise,
                                Map<String, PackImageData> images) {
        this(pack, variants, noise, images, new WorldProgramPlan(Map.of()));
    }

    public PreparedPackPipeline {
        images = Map.copyOf(images);
        variants = Map.copyOf(variants);
        world = world == null ? new WorldProgramPlan(Map.of()) : world;
        materials = java.util.Objects.requireNonNull(materials);
        items = java.util.Objects.requireNonNull(items);
        materialFormat=java.util.Objects.requireNonNull(materialFormat);
        optionValues=Map.copyOf(optionValues);
        if (worldNoiseImageKey != null && !images.containsKey(worldNoiseImageKey))
            throw new IllegalArgumentException("Missing prepared native noise image");
        if (variants.isEmpty()) throw new IllegalArgumentException("A pipeline needs at least one dimension variant");
    }

    public List<PreparedFinalProgram> programs() {
        return variants.values().stream().flatMap(variant -> variant.programs().stream()).toList();
    }

    public record Variant(List<PreparedFinalProgram> programs, ColorBufferPlan buffers, double eyeBrightnessHalfLife,
                          CustomUniformBindings customUniforms, WetnessSettings wetnessSettings) {
        public Variant(List<PreparedFinalProgram> programs,ColorBufferPlan buffers,double eyeBrightnessHalfLife,
                       CustomUniformBindings customUniforms) {
            this(programs,buffers,eyeBrightnessHalfLife,customUniforms,WetnessSettings.DEFAULT);
        }
        public Variant(List<PreparedFinalProgram> programs,ColorBufferPlan buffers,double eyeBrightnessHalfLife) {
            this(programs,buffers,eyeBrightnessHalfLife,CustomUniformBindings.EMPTY);
        }
        public Variant(List<PreparedFinalProgram> programs, ColorBufferPlan buffers) {
            this(programs, buffers, EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS);
        }
        public Variant {
            java.util.Objects.requireNonNull(wetnessSettings);
            java.util.Objects.requireNonNull(customUniforms);
            if (!Double.isFinite(eyeBrightnessHalfLife) || eyeBrightnessHalfLife < 0)
                throw new IllegalArgumentException("Invalid eye-brightness half-life");
            programs = List.copyOf(programs);
            if (programs.isEmpty()) throw new IllegalArgumentException("A variant needs at least one program");
        }
    }
}
