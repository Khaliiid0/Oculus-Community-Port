package dev.oculusforge.mixin;

import dev.oculusforge.render.world.EyePositionState;
import dev.oculusforge.render.world.RelativeEyePosition;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CameraRenderState.class, remap = false)
abstract class CameraRenderStateMixin implements EyePositionState, dev.oculusforge.render.world.WorldEnvironmentState {
    @Unique private dev.oculusforge.render.world.WorldEnvironment oculus$environment;
    public dev.oculusforge.render.world.WorldEnvironment oculus$getEnvironment() { return oculus$environment; }
    public void oculus$setEnvironment(dev.oculusforge.render.world.WorldEnvironment value) { oculus$environment = value; }
    @Unique private RelativeEyePosition oculus$relativeEye;
    public RelativeEyePosition oculus$getRelativeEye() { return oculus$relativeEye; }
    public void oculus$setRelativeEye(RelativeEyePosition value) { oculus$relativeEye = value; }
}
