package dev.oculusforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.oculusforge.render.world.BlockLayerOverrides;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = SectionCompiler.class, remap = false)
abstract class SectionLayerRoutingMixin {
    @WrapMethod(method = "compile")
    private SectionCompiler.Results oculus$pinLayers(SectionPos section, RenderSectionRegion region,
        VertexSorting sorting, SectionBufferBuilderPack buffers, Operation<SectionCompiler.Results> original) {
        return BlockLayerOverrides.ROUTING.compile(() -> original.call(section, region, sorting, buffers));
    }

    // Both ordinary material-based output and forceOpaque(leaves) output reach this seam.
    // The map key, builder, sorting and uploaded mesh all use the overridden layer.
    @ModifyVariable(method = "getOrBeginLayer", at = @At("HEAD"), argsOnly = true)
    private ChunkSectionLayer oculus$routeLayer(ChunkSectionLayer vanilla) {
        return BlockLayerOverrides.layer(vanilla);
    }
}
