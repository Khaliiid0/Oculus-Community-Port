package dev.oculusforge.render.program;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.oculusforge.render.world.WorldEnvironment;
import dev.oculusforge.render.world.WorldEnvironmentState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PackFinalProgram implements AutoCloseable {
   private final String packId;
   private final String stageName;
   private final RenderPipeline pipeline;
   private final List<LegacyFinalShaderAdapter.SamplerBinding> samplers;
   private final List<LegacyFinalShaderAdapter.UniformBinding> uniforms;
   private final MappableRingBuffer uniformBuffer;
   private final boolean zeroToOneDepth;
   private final ItemMaterialMap items;
   private boolean closed;
   private static final java.util.concurrent.atomic.AtomicInteger LIVE_UNIFORM_RINGS = new java.util.concurrent.atomic.AtomicInteger();
   private float lastFrameTime;
   private float smoothedFrameTime;
   private float wetness;
   private float centerDepthSmooth = 1.0F;
   private boolean hasCenterDepth;
   private double previousCameraX;
   private double previousCameraY;
   private double previousCameraZ;
   private boolean hasPreviousCamera;
   private Matrix4f previousProjection;
   private Matrix4f previousModelView;
   private Object temporalWorld;
   private EyeBrightnessSmoother.Value smoothedEyeBrightness;
   private final CameraHistory localCameraHistory = new CameraHistory();

   /** Render-thread only. Also releases the old world's identity on logout. */
   public void resetTemporalHistory() {
      localCameraHistory.reset();
      temporalWorld = null;
      smoothedEyeBrightness = null;
      lastFrameTime = 0;
      smoothedFrameTime = 0;
      wetness = 0;
      centerDepthSmooth = 1;
      hasCenterDepth = false;
      previousCameraX = previousCameraY = previousCameraZ = 0;
      hasPreviousCamera = false;
      previousProjection = null;
      previousModelView = null;
   }

   void prepareWorld(Object world) {
      java.util.Objects.requireNonNull(world, "No active client world");
      if (temporalWorld != world) {
         resetTemporalHistory();
         temporalWorld = world;
      }
   }

   void prepareEyeBrightness(Object world, AnimationClock.Snapshot animation,
                             WorldEnvironment environment, EyeBrightnessSmoother shared) {
      prepareWorld(world);
      smoothedEyeBrightness = shared.update(world, animation, environment.eyeBrightness(), animation.frameTime());
   }

   void prepareCamera(Object world, Object frame, double x, double y, double z, CameraHistory shared) {
      // Validate before any per-program reset, including the convenience local tracker.
      java.util.Objects.requireNonNull(world);
      java.util.Objects.requireNonNull(frame);
      java.util.Objects.requireNonNull(shared);
      new CameraHistory.Position(x,y,z);
      prepareWorld(world);
      var snapshot = shared.update(world,frame,x,y,z);
      previousCameraX = snapshot.previous().x();
      previousCameraY = snapshot.previous().y();
      previousCameraZ = snapshot.previous().z();
      hasPreviousCamera = true;
   }

   PackFinalProgram(
      String packId,
      String stageName,
      RenderPipeline pipeline,
      List<LegacyFinalShaderAdapter.SamplerBinding> samplers,
      List<LegacyFinalShaderAdapter.UniformBinding> uniforms,
      MappableRingBuffer uniformBuffer
   ) {
      this(packId, stageName, pipeline, samplers, uniforms, uniformBuffer, false);
   }

   PackFinalProgram(String packId, String stageName, RenderPipeline pipeline,
      List<LegacyFinalShaderAdapter.SamplerBinding> samplers,
      List<LegacyFinalShaderAdapter.UniformBinding> uniforms, MappableRingBuffer uniformBuffer,
      boolean zeroToOneDepth) {
      this(packId,stageName,pipeline,samplers,uniforms,uniformBuffer,zeroToOneDepth,ItemMaterialMap.EMPTY);
   }

   PackFinalProgram(String packId,String stageName,RenderPipeline pipeline,
      List<LegacyFinalShaderAdapter.SamplerBinding> samplers,List<LegacyFinalShaderAdapter.UniformBinding> uniforms,
      MappableRingBuffer uniformBuffer,boolean zeroToOneDepth,ItemMaterialMap items) {
      this.items=java.util.Objects.requireNonNull(items);
      this.zeroToOneDepth = zeroToOneDepth;
      this.packId = packId;
      this.stageName = stageName;
      this.pipeline = pipeline;
      this.samplers = List.copyOf(samplers);
      this.uniforms = List.copyOf(uniforms);
      this.uniformBuffer = uniformBuffer;
      if (uniformBuffer != null) LIVE_UNIFORM_RINGS.incrementAndGet();
   }

   public String packId() {
      return this.packId;
   }

   public RenderPipeline pipeline() {
      return this.pipeline;
   }

   public String stageName() {
      return this.stageName;
   }

   public List<LegacyFinalShaderAdapter.SamplerBinding> samplers() {
      return this.samplers;
   }

   public static int liveUniformRings() { return LIVE_UNIFORM_RINGS.get(); }

   public boolean hasUniforms() {
      return this.uniformBuffer != null;
   }

   public GpuBuffer updateUniforms(LevelRenderState state, int width, int height, EyeBrightnessSmoother eyeBrightness) {
      return updateUniforms(state,width,height,eyeBrightness,CustomUniformBindings.EMPTY.newState());
   }

   public GpuBuffer updateUniforms(LevelRenderState state, int width, int height, EyeBrightnessSmoother eyeBrightness,
                                  CustomUniformBindings.State customUniforms) {
      return updateUniforms(state,width,height,eyeBrightness,customUniforms,localCameraHistory);
   }

   public GpuBuffer updateUniforms(LevelRenderState state, int width, int height, EyeBrightnessSmoother eyeBrightness,
                                  CustomUniformBindings.State customUniforms, CameraHistory cameraHistory) {
      if (this.uniformBuffer == null) {
         throw new IllegalStateException("This shader program has no legacy uniform buffer");
      } else {
         var environment = ((WorldEnvironmentState) state.cameraRenderState).oculus$requireEnvironment();
         var clockLevel = Minecraft.getInstance().level;
         var animation = RenderAnimationClock.current();
         prepareEyeBrightness(clockLevel, animation, environment, eyeBrightness);
         prepareCamera(clockLevel,animation,state.cameraRenderState.pos.x,state.cameraRenderState.pos.y,
             state.cameraRenderState.pos.z,cameraHistory);
         float deltaSeconds = Math.min(animation.frameTime(), 1.0F);
          this.lastFrameTime = animation.frameTime();
          float temporalBlend = deltaSeconds <= 0.0F ? 0.0F : 1.0F - (float)Math.exp(-deltaSeconds * 5.0F);
          this.smoothedFrameTime = this.smoothedFrameTime == 0.0F ? deltaSeconds
             : this.smoothedFrameTime + (deltaSeconds - this.smoothedFrameTime) * temporalBlend;
         float rain = environment.rainStrength();
         var worldClock = WorldClockUniforms.fromTicks(clockLevel == null ? 0L : clockLevel.getOverworldClockTime());
         var customValues=customUniforms.update(java.util.Objects.requireNonNull(clockLevel,"No active client world"),
             animation,clockLevel.getOverworldClockTime(),state.cameraRenderState.pos.x,
             state.cameraRenderState.pos.y,state.cameraRenderState.pos.z,environment.biomeCode(),
             environment.blindness(),environment.darknessFactor(),environment.rainStrength(),environment.eyeBrightness(),environment.precipitation());
         float halfLife = rain > this.wetness ? 600.0F : 200.0F;
         float blend = deltaSeconds <= 0.0F ? 0.0F : 1.0F - (float)Math.pow(0.5, (double)(deltaSeconds * 20.0F / halfLife));
          this.wetness = this.wetness + (rain - this.wetness) * blend;
          float centerDepth = centerDepth(state);
          this.centerDepthSmooth = this.hasCenterDepth
             ? this.centerDepthSmooth + (centerDepth - this.centerDepthSmooth) * temporalBlend : centerDepth;
          this.hasCenterDepth = true;
         MappedView mapped = this.uniformBuffer.currentBuffer().map(false, true);

         try {
            Std140Builder builder = Std140Builder.intoBuffer(mapped.data());

            for (LegacyFinalShaderAdapter.UniformBinding uniform : this.uniforms) {
               if(uniform.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CUSTOM_FLOAT)
                  builder.putFloat(customValues.boundSlot(CustomUniformBindings.index(uniform.name())));
               else this.writeUniform(builder, uniform.semantic(), state, width, height, environment, worldClock, animation);
            }
         } catch (Throwable var15) {
            if (mapped != null) {
               try {
                  mapped.close();
               } catch (Throwable var14) {
                  var15.addSuppressed(var14);
               }
            }

            throw var15;
         }

         if (mapped != null) {
            mapped.close();
         }

         this.previousCameraX = state.cameraRenderState.pos.x;
         this.previousCameraY = state.cameraRenderState.pos.y;
         this.previousCameraZ = state.cameraRenderState.pos.z;
         this.hasPreviousCamera = true;
         this.previousProjection = new Matrix4f(state.cameraRenderState.projectionMatrix);
         this.previousModelView = new Matrix4f(state.cameraRenderState.viewRotationMatrix);
         return this.uniformBuffer.currentBuffer();
      }
   }

   public void finishFrame() {
      if (this.uniformBuffer != null) {
         this.uniformBuffer.rotate();
      }
   }

   private void writeUniform(
      Std140Builder builder, LegacyFinalShaderAdapter.UniformSemantic semantic, LevelRenderState state, int width, int height, WorldEnvironment environment,
      WorldClockUniforms worldClock, AnimationClock.Snapshot animation
   ) {
      switch (semantic) {
         case VIEW_WIDTH:
            builder.putFloat((float)width);
            break;
         case VIEW_HEIGHT:
            builder.putFloat((float)height);
            break;
         case ASPECT_RATIO:
            builder.putFloat(height == 0 ? 1.0F : (float)width / (float)height);
            break;
         case FRAME_TIME:
            builder.putFloat(this.lastFrameTime);
            break;
         case FRAME_TIME_SMOOTH:
            builder.putFloat(this.smoothedFrameTime);
            break;
         case FRAME_TIME_COUNTER:
            builder.putFloat(animation.frameTimeCounter());
            break;
         case FRAME_COUNTER:
            builder.putInt(animation.frameCounter());
            break;
         case WORLD_TIME:
            builder.putInt(worldClock.time());
            break;
         case WORLD_DAY:
            builder.putInt(worldClock.day());
            break;
         case MOON_PHASE:
            builder.putInt(environment.moonPhase());
            break;
         case RAIN_STRENGTH:
            builder.putFloat(environment.rainStrength());
            break;
         case WETNESS:
            builder.putFloat(this.wetness);
            break;
         case EYE_ALTITUDE:
            builder.putFloat((float)state.cameraRenderState.pos.y);
            break;
         case NEAR:
            builder.putFloat(0.05F);
            break;
         case FAR:
            builder.putFloat(state.cameraRenderState.depthFar);
            break;
         case SUN_ANGLE:
         case TIME_ANGLE:
            builder.putFloat(dev.oculusforge.render.world.NativeSceneInputs.sunAngleTurns(state.skyRenderState.sunAngle));
            break;
         case SCREEN_BRIGHTNESS:
            builder.putFloat(Minecraft.getInstance().options.gamma().get().floatValue());
            break;
         case IS_EYE_IN_WATER:
            builder.putInt(eyeMedium(state.cameraRenderState.fogType));
            break;
         case CAMERA_POSITION:
            builder.putVec3((float)state.cameraRenderState.pos.x, (float)state.cameraRenderState.pos.y, (float)state.cameraRenderState.pos.z);
            break;
         case PREVIOUS_CAMERA_POSITION:
            builder.putVec3(
               (float)(this.hasPreviousCamera ? this.previousCameraX : state.cameraRenderState.pos.x),
               (float)(this.hasPreviousCamera ? this.previousCameraY : state.cameraRenderState.pos.y),
               (float)(this.hasPreviousCamera ? this.previousCameraZ : state.cameraRenderState.pos.z));
            break;
         case FOG_COLOR:
            builder.putVec3(environment.fog().red(),environment.fog().green(),environment.fog().blue());
            break;
         case LEGACY_FOG_COLOR:
            builder.putVec4(environment.fog().red(),environment.fog().green(),environment.fog().blue(),environment.fog().alpha());
            break;
         case LEGACY_FOG_START:
            builder.putFloat(environment.fog().start());
            break;
         case LEGACY_FOG_END:
            builder.putFloat(environment.fog().end());
            break;
         case SKY_COLOR:
            var skyColor = environment.skyColor();
            builder.putVec3(skyColor.red(),skyColor.green(),skyColor.blue());
            break;
         case SUN_POSITION:
            Vector3f sun = new Vector3f(0.0F, 100.0F, 0.0F)
               .rotateZ(state.skyRenderState.sunAngle)
               .mulDirection(state.cameraRenderState.viewRotationMatrix);
            builder.putVec3(sun.x, sun.y, sun.z);
            break;
         case END_FLASH_INTENSITY:
            builder.putFloat(state.skyRenderState.endFlashIntensity);
            break;
         case END_FLASH_POSITION:
            Vector3f flash = new Vector3f(0.0F, 0.0F, -100.0F)
               .rotateX((float)Math.toRadians(state.skyRenderState.endFlashXAngle))
               .rotateY((float)Math.toRadians(state.skyRenderState.endFlashYAngle))
               .mulDirection(state.cameraRenderState.viewRotationMatrix);
            builder.putVec3(flash.x, flash.y, flash.z);
            break;
         case NIGHT_VISION:
            builder.putFloat(environment.nightVision());
            break;
         case BLIND_FACTOR:
            builder.putFloat(environment.blindness());
            break;
         case DARKNESS_FACTOR:
            builder.putFloat(environment.darknessFactor());
            break;
         case DARKNESS_LIGHT_FACTOR:
            builder.putFloat(environment.darknessLightFactor());
            break;
         case MAX_BLINDNESS_DARKNESS:
            builder.putFloat(Math.max(environment.blindness(),environment.darknessFactor()));
            break;
         case IN_BASALT_DELTAS:
            builder.putFloat(biomeMatches(state, "basalt_deltas") ? 1.0F : 0.0F);
            break;
         case IN_CRIMSON_FOREST:
            builder.putFloat(biomeMatches(state, "crimson_forest") ? 1.0F : 0.0F);
            break;
         case IN_NETHER_WASTES:
            builder.putFloat(biomeMatches(state, "nether_wastes") ? 1.0F : 0.0F);
            break;
         case IN_SOUL_VALLEY:
            builder.putFloat(biomeMatches(state, "soul_sand_valley") ? 1.0F : 0.0F);
            break;
         case IN_WARPED_FOREST:
            builder.putFloat(biomeMatches(state, "warped_forest") ? 1.0F : 0.0F);
            break;
         case SHADOW_FADE:
            builder.putFloat(1.0F);
            break;
         case TIME_BRIGHTNESS:
            builder.putFloat(Math.max(0.0F, 1.0F - state.skyRenderState.starBrightness));
            break;
         case EYE_BRIGHTNESS:
            builder.putIVec2(environment.eyeBrightness().blockLightmap(), environment.eyeBrightness().skyLightmap());
            break;
         case EYE_BRIGHTNESS_SMOOTH:
            var smooth = java.util.Objects.requireNonNull(smoothedEyeBrightness, "Missing shared eye-brightness frame snapshot");
            builder.putIVec2(smooth.block(), smooth.sky());
            break;
         case EYE_BRIGHTNESS_MAX:
            builder.putFloat(environment.eyeBrightness().maximum());
            break;
         case IN_DRY:
            builder.putFloat(biomeMatches(state, "desert", "badlands", "mesa", "savanna") ? 1.0F : 0.0F);
            break;
         case IN_SNOWY:
            builder.putFloat(biomeMatches(state, "snow", "frozen", "ice", "cold", "grove") ? 1.0F : 0.0F);
            break;
         case IN_RAINY:
            var rainyLevel = Minecraft.getInstance().level;
            boolean rainy = rainyLevel != null && rainyLevel.getBiome(state.cameraRenderState.blockPos).value()
               .getPrecipitationAt(state.cameraRenderState.blockPos, rainyLevel.getSeaLevel())
               == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
            builder.putFloat(rainy ? 1.0F : 0.0F);
            break;
         case IN_PALE_GARDEN:
            builder.putFloat(biomeMatches(state, "pale_garden") ? 1.0F : 0.0F);
            break;
         case CENTER_DEPTH_SMOOTH:
            builder.putFloat(this.centerDepthSmooth);
            break;
         case CAMERA_VELOCITY:
            if (!this.hasPreviousCamera || this.lastFrameTime <= 0.0F) builder.putFloat(0.0F);
            else {
               double dx = state.cameraRenderState.pos.x - this.previousCameraX;
               double dy = state.cameraRenderState.pos.y - this.previousCameraY;
               double dz = state.cameraRenderState.pos.z - this.previousCameraZ;
               builder.putFloat((float)(Math.sqrt(dx * dx + dy * dy + dz * dz) / this.lastFrameTime));
            }
            break;
         case IS_DESERT:
            builder.putFloat(biomeMatches(state, "desert") ? 1.0F : 0.0F);
            break;
         case IS_MESA:
            builder.putFloat(biomeMatches(state, "badlands", "mesa") ? 1.0F : 0.0F);
            break;
         case IS_COLD:
            builder.putFloat(biomeMatches(state, "snow", "frozen", "ice", "cold") ? 1.0F : 0.0F);
            break;
         case IS_SWAMP:
            builder.putFloat(biomeMatches(state, "swamp") ? 1.0F : 0.0F);
            break;
         case IS_MUSHROOM:
            builder.putFloat(biomeMatches(state, "mushroom") ? 1.0F : 0.0F);
            break;
         case IS_SAVANNA:
            builder.putFloat(biomeMatches(state, "savanna") ? 1.0F : 0.0F);
            break;
         case IS_JUNGLE:
            builder.putFloat(biomeMatches(state, "jungle") ? 1.0F : 0.0F);
            break;
         case RAIN_FACTOR:
            builder.putFloat(environment.rainStrength());
            break;
         case CLOUD_HEIGHT:
            builder.putFloat(environment.cloudHeight());
            break;
         case ATLAS_SIZE:
            builder.putIVec2(environment.atlasSize().width(),environment.atlasSize().height());
            break;
         case HELD_ITEM_ID:
            builder.putInt(items.id(environment.heldItems().mainHand()));
            break;
         case HELD_ITEM_ID_2:
            builder.putInt(items.id(environment.heldItems().offHand()));
            break;
         case CAMERA_POSITION_INT:
            builder.putIVec3(floorToInt(state.cameraRenderState.pos.x), floorToInt(state.cameraRenderState.pos.y), floorToInt(state.cameraRenderState.pos.z));
            break;
         case CAMERA_POSITION_FRACT:
            builder.putVec3(fract(state.cameraRenderState.pos.x), fract(state.cameraRenderState.pos.y), fract(state.cameraRenderState.pos.z));
            break;
         case PREVIOUS_CAMERA_POSITION_FRACT:
            builder.putVec3(
               fract(this.hasPreviousCamera ? this.previousCameraX : state.cameraRenderState.pos.x),
               fract(this.hasPreviousCamera ? this.previousCameraY : state.cameraRenderState.pos.y),
               fract(this.hasPreviousCamera ? this.previousCameraZ : state.cameraRenderState.pos.z));
            break;
         case GBUFFER_PROJECTION:
            builder.putMat4f(legacyProjection(state.cameraRenderState.projectionMatrix));
            break;
         case GBUFFER_PROJECTION_INVERSE:
            builder.putMat4f(legacyProjection(state.cameraRenderState.projectionMatrix).invert());
            break;
         case GBUFFER_MODEL_VIEW:
            builder.putMat4f(state.cameraRenderState.viewRotationMatrix);
            break;
         case GBUFFER_MODEL_VIEW_INVERSE:
            builder.putMat4f(new Matrix4f(state.cameraRenderState.viewRotationMatrix).invert());
            break;
         case GBUFFER_PREVIOUS_PROJECTION:
            builder.putMat4f(legacyProjection(this.previousProjection == null ? state.cameraRenderState.projectionMatrix : this.previousProjection));
            break;
         case GBUFFER_PREVIOUS_PROJECTION_INVERSE:
            builder.putMat4f(legacyProjection(this.previousProjection == null ? state.cameraRenderState.projectionMatrix : this.previousProjection).invert());
            break;
         case GBUFFER_PREVIOUS_MODEL_VIEW:
            builder.putMat4f(this.previousModelView == null ? state.cameraRenderState.viewRotationMatrix : this.previousModelView);
            break;
         case GBUFFER_PREVIOUS_MODEL_VIEW_INVERSE:
            builder.putMat4f(new Matrix4f(this.previousModelView == null ? state.cameraRenderState.viewRotationMatrix : this.previousModelView).invert());
            break;
         case SHADOW_PROJECTION:
            builder.putMat4f(dev.oculusforge.render.world.WorldRenderHooks.shadowProjection());
            break;
         case SHADOW_PROJECTION_INVERSE:
            builder.putMat4f(dev.oculusforge.render.world.WorldRenderHooks.shadowProjection().invert());
            break;
         case SHADOW_MODEL_VIEW:
            builder.putMat4f(dev.oculusforge.render.world.WorldRenderHooks.shadowModelView());
            break;
         case SHADOW_MODEL_VIEW_INVERSE:
            builder.putMat4f(dev.oculusforge.render.world.WorldRenderHooks.shadowModelView().invert());
      }
   }

   private static int floorToInt(double value) {
      return (int)Math.floor(value);
   }

   private Matrix4f legacyProjection(org.joml.Matrix4fc projection) {
      return LegacyProjection.fromReverseDepth(projection, zeroToOneDepth);
   }

   private static float fract(double value) {
      return (float)(value - Math.floor(value));
   }

   private static boolean biomeMatches(LevelRenderState state, String... fragments) {
      var level = Minecraft.getInstance().level;
      if (level == null) return false;
      String path = level.getBiome(state.cameraRenderState.blockPos).unwrapKey()
         .map(key -> key.identifier().getPath()).orElse("");
      for (String fragment : fragments) if (path.contains(fragment)) return true;
      return false;
   }

   private static float centerDepth(LevelRenderState state) {
      var hit = Minecraft.getInstance().hitResult;
      if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return 1.0F;
      float near = 0.05F;
      float far = Math.max(near + 0.001F, state.cameraRenderState.depthFar);
      float distance = Math.max(near, (float)state.cameraRenderState.pos.distanceTo(hit.getLocation()));
      return Math.max(0.0F, Math.min(1.0F, far * (distance - near) / (distance * (far - near))));
   }

   private static int eyeMedium(FogType fogType) {
      return dev.oculusforge.render.world.NativeFogData.eyeMedium(fogType);
   }

   static int uniformBufferSize(List<LegacyFinalShaderAdapter.UniformBinding> uniforms) {
      Std140SizeCalculator size = new Std140SizeCalculator();

      for (LegacyFinalShaderAdapter.UniformBinding uniform : uniforms) {
         switch (uniform.semantic().type()) {
            case FLOAT:
               size.putFloat();
               break;
            case INT:
               size.putInt();
               break;
            case VEC3:
               size.putVec3();
               break;
            case VEC4:
               size.putVec4();
               break;
            case IVEC3:
               size.putIVec3();
               break;
            case IVEC2:
               size.putIVec2();
               break;
            case MAT4:
               size.putMat4f();
         }
      }

      return size.align(16).get();
   }

   @Override
   public void close() {
      if (closed) return;
      closed = true;
      resetTemporalHistory();
      if (this.uniformBuffer != null) {
         this.uniformBuffer.close();
         LIVE_UNIFORM_RINGS.decrementAndGet();
      }
   }
}
