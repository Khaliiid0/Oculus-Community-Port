package dev.oculusforge.mixin;

import dev.oculusforge.render.world.EyePositionState;
import dev.oculusforge.render.world.RelativeEyePosition;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, remap = false)
abstract class CameraMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void oculus$captureEye(CameraRenderState state, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        // Use the camera's extracted interpolation, including entity freeze handling.
        float partialTicks = state.cameraEntityPartialTicks;
        var target = (EyePositionState) state;
        target.oculus$setRelativeEye(null);
        var environment = (dev.oculusforge.render.world.WorldEnvironmentState) state;
        environment.oculus$setEnvironment(null);
        var entity = ((Camera) (Object) this).entity();
        if (!state.initialized || entity == null) return;
        // Chunk payloads and their lighting are applied separately on the client.
        // Do not seed temporal history with the light engine's unloaded-column zero.
        // Keep the snapshot absent so native and post rendering both defer together.
        int chunkX = state.blockPos.getX() >> 4, chunkZ = state.blockPos.getZ() >> 4;
        long lightColumn = net.minecraft.core.SectionPos.getZeroNode(chunkX, chunkZ);
        if (!entity.level().getChunkSource().hasChunk(chunkX, chunkZ)
            || !entity.level().getChunkSource().getLightEngine().lightOnInColumn(lightColumn)) return;
        var eye = entity.getEyePosition(partialTicks);
        try {
            environment.oculus$setEnvironment(new dev.oculusforge.render.world.WorldEnvironment(
                ((Camera) (Object) this).attributeProbe().getValue(
                    net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, partialTicks).index(),
                entity.level().getRainLevel(partialTicks),
                entity instanceof net.minecraft.world.entity.LivingEntity living
                    && living.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                    ? net.minecraft.client.renderer.GameRenderer.nightVisionScale(living, partialTicks) : 0,
                new dev.oculusforge.render.world.EyeBrightness(
                    entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, state.blockPos),
                    entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, state.blockPos)),
                entity.level().getBiome(state.blockPos).unwrapKey().map(key ->
                    dev.oculusforge.render.world.NativeBiomeSymbols.fromKey(key.identifier().toString())).orElse(0),
                oculus$effectFactor(entity,net.minecraft.world.effect.MobEffects.BLINDNESS,partialTicks),
                oculus$effectFactor(entity,net.minecraft.world.effect.MobEffects.DARKNESS,partialTicks),
                ((Camera) (Object) this).attributeProbe().getValue(
                    net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT,partialTicks),
                switch(entity.level().getBiome(state.blockPos).value().getPrecipitationAt(state.blockPos,entity.level().getSeaLevel())) {
                    case NONE -> 0;
                    case RAIN -> 1;
                    case SNOW -> 2;
                },dev.oculusforge.render.world.AtlasSize.capture(),
                dev.oculusforge.render.world.HeldItemKeys.capture(net.minecraft.client.Minecraft.getInstance().player),
                // GameRenderer extracts this lightmap state before extracting the camera.
                // Reuse its exact pulse/option/tick result, including between lightmap updates.
                dev.oculusforge.render.world.DarknessLight.capture(net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.gameRenderState().lightmapRenderState)));
            target.oculus$setRelativeEye(RelativeEyePosition.between(
                state.pos.x, state.pos.y, state.pos.z, eye.x, eye.y, eye.z));
        } catch (IllegalArgumentException invalidPosition) {
            // Native draws defer instead of uploading invalid or stale coordinates.
        }
    }
    @org.spongepowered.asm.mixin.Unique
    private static float oculus$effectFactor(net.minecraft.world.entity.Entity entity,
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,float partialTicks) {
        if(!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return 0;
        var instance=living.getEffect(effect);
        return instance==null?0:instance.getBlendFactor(living,partialTicks);
    }
}
