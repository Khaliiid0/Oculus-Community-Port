package dev.oculusforge.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.oculusforge.render.world.ExtendedTerrainMesh;
import dev.oculusforge.render.world.QuadMeshAttributes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.lwjgl.system.MemoryUtil;
import dev.oculusforge.render.world.MaterialVertexContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BufferBuilder.class, remap = false)
abstract class TerrainBufferBuilderMixin {
    @Shadow @Final private VertexFormat format;
    @Shadow @Final @Mutable private boolean blockFormat;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void oculus$keepBaseVertexFastPath(CallbackInfo callback) {
        // The first 28 bytes match BLOCK exactly. Extra attributes are filled after all
        // vertices exist. Stable state identity is captured separately during each write.
        if (format == ExtendedTerrainMesh.FORMAT) blockFormat = true;
    }

    @Redirect(method = "beginVertex", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;reserve(I)J"))
    private long oculus$captureMaterialState(ByteBufferBuilder storage, int size) {
        long pointer = storage.reserve(size);
        if (format == ExtendedTerrainMesh.FORMAT) MemoryUtil.memPutInt(pointer + 44, MaterialVertexContext.current());
        return pointer;
    }

    @Inject(method = "build", at = @At("RETURN"))
    private void oculus$enrichQuads(CallbackInfoReturnable<MeshData> callback) {
        MeshData mesh = callback.getReturnValue();
        if (mesh == null || format != ExtendedTerrainMesh.FORMAT) return;
        try { QuadMeshAttributes.fill(mesh.vertexBuffer(), mesh.drawState().vertexCount()); }
        catch (RuntimeException failure) { mesh.close(); throw failure; }
    }
}
