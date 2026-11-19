package dev.oculusforge.mixin;

import dev.oculusforge.render.program.TerrainMeshMode;
import dev.oculusforge.render.world.MaterialVertexContext;
import dev.oculusforge.render.world.BlockLayerOverrides;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraftforge.registries.GameData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SectionCompiler.class, remap = false)
abstract class SectionMaterialMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;JLnet/minecraftforge/client/model/data/ModelData;)V"))
    private void oculus$blockState(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
        BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed, ModelData modelData) {
        var previousLayer = BlockLayerOverrides.ROUTING.enterBlock(state.getBlock());
        try {
            if (!TerrainMeshMode.EXTENDED) { renderer.tesselateBlock(output, x,y,z,level,pos,state,model,seed,modelData); return; }
            int previous = MaterialVertexContext.enter(MaterialVertexContext.encode(GameData.BlockCallbacks.getBlockStateIDMap().getId(state), false));
            try { renderer.tesselateBlock(output, x,y,z,level,pos,state,model,seed,modelData); }
            finally { MaterialVertexContext.restore(previous); }
        } finally { BlockLayerOverrides.ROUTING.restoreBlock(previousLayer); }
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
    private void oculus$fluidState(FluidRenderer renderer, BlockAndTintGetter level, BlockPos pos,
        FluidRenderer.Output output, BlockState block, FluidState fluid) {
        if (!TerrainMeshMode.EXTENDED) { renderer.tesselate(level,pos,output,block,fluid); return; }
        // Waterlogged geometry must identify the fluid, not the surrounding block.
        int previous = MaterialVertexContext.enter(MaterialVertexContext.encode(GameData.BlockCallbacks.getBlockStateIDMap().getId(fluid.createLegacyBlock()), true));
        try { renderer.tesselate(level,pos,output,block,fluid); }
        finally { MaterialVertexContext.restore(previous); }
    }
}
