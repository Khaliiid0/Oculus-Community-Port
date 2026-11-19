package dev.oculusforge.client;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oculusforge.OculusForge;
import dev.oculusforge.config.OculusConfig;
import dev.oculusforge.config.OculusConfigStore;
import dev.oculusforge.render.BackendPolicy;
import dev.oculusforge.render.GraphicsApi;
import dev.oculusforge.render.pipeline.OculusCompositePass;
import dev.oculusforge.render.pipeline.ShaderPipelineManager;
import dev.oculusforge.render.program.PackProgramCompiler;
import dev.oculusforge.render.program.PreparedPackPipeline;
import dev.oculusforge.render.world.WorldRenderHooks;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import dev.oculusforge.shaderpack.ShaderPackRepository;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class OculusClientService {
   private static final SystemToastId BACKEND_WARNING_TOAST = new SystemToastId(10000L);
   private final OculusConfigStore configStore;
   private final ShaderPackRepository repository;
   private final PackProgramCompiler compiler;
   private final ShaderPipelineManager pipelineManager;
   private final OculusCompositePass compositePass;
   private final AtomicReference<ShaderPackRepository.DiscoveryResult> discovery = new AtomicReference<>(
      new ShaderPackRepository.DiscoveryResult(List.of(), List.of())
   );
   private volatile OculusConfig config = OculusConfig.DEFAULT;
   private final ShaderReloadState reloadState = new ShaderReloadState(OculusConfig.DEFAULT);
   private final LatestTaskExecutor shaderPreparation = new LatestTaskExecutor();
   private volatile CompletableFuture<Void> selectionFuture = CompletableFuture.completedFuture(null);
   private PendingSelection pendingSelection;
   private boolean graphicsInvalidated;
   private volatile OculusClientService.ReloadPreparation lastPreparation;
   private volatile PreparedPackPipeline lastKnownGoodPreparation;
   private volatile GraphicsApi graphicsApi = GraphicsApi.UNKNOWN;
   private volatile String backendName = "not initialized";
   private volatile BackendPolicy.Decision decision = BackendPolicy.Decision.DISABLE_UNKNOWN_BACKEND;

   private OculusClientService(OculusConfigStore configStore, ShaderPackRepository repository, PackProgramCompiler compiler) {
      this.configStore = configStore;
      this.repository = repository;
      this.compiler = compiler;
      this.pipelineManager = new ShaderPipelineManager(compiler);
      WorldRenderHooks.install(this.pipelineManager);
      this.compositePass = new OculusCompositePass(this.pipelineManager);
   }

   public static OculusClientService createDefault() {
      OculusConfigStore configStore = new OculusConfigStore(FMLPaths.CONFIGDIR.get().resolve("oculus.properties"));
      OculusClientService service = new OculusClientService(
         configStore,
         new ShaderPackRepository(FMLPaths.GAMEDIR.get().resolve("shaderpacks"), ShaderPackLimits.DEFAULT),
         new PackProgramCompiler(ShaderPackLimits.DEFAULT)
      );

      try {
         service.config = configStore.load();
      } catch (IOException var3) {
         OculusForge.LOGGER.error("Could not load Oculus configuration from {}", configStore.path(), var3);
      }

      service.reloadState.select(service.config);
      return service;
   }

   public void initializeOnRenderThread() {
      RenderSystem.assertOnRenderThread();
      GpuDevice device = RenderSystem.tryGetDevice();
      this.backendName = device == null ? "unavailable" : device.getDeviceInfo().backendName();
      this.graphicsApi = GraphicsApi.fromBackendName(this.backendName);
      boolean optiFinePresent = detectOptiFine();
      this.decision = BackendPolicy.evaluate(this.graphicsApi, optiFinePresent, true);
      OculusForge.LOGGER
         .info("Oculus graphics backend: {} ({}); shader pipeline decision: {}", new Object[]{this.backendName, this.graphicsApi, this.decision});
      if (this.config.shadersEnabled() && this.decision != BackendPolicy.Decision.ALLOW_OPENGL_PIPELINE) {
         this.config = this.config.withShadersEnabled(false);
         this.saveConfig();
         OculusForge.LOGGER.warn("Disabled configured shader rendering because the backend policy is {}", this.decision);
      }

      if (this.decision != BackendPolicy.Decision.ALLOW_OPENGL_PIPELINE) {
         this.showSafetyWarning();
      }

      if (this.lastPreparation != null) {
         this.reconcilePipeline(this.lastPreparation, true);
      }
   }

   private static boolean detectOptiFine() {
      if (!ModList.isLoaded("optifine") && !ModList.isLoaded("optiforge")) {
         try {
            Class.forName("net.optifine.Config", false, OculusClientService.class.getClassLoader());
            return true;
         } catch (ClassNotFoundException var1) {
            return false;
         } catch (LinkageError var2) {
            OculusForge.LOGGER.warn("OptiFine class probe encountered an incompatible class", var2);
            return true;
         }
      } else {
         return true;
      }
   }

   private void showSafetyWarning() {
      Minecraft minecraft = Minecraft.getInstance();

      Component detail = switch (this.decision) {
         case DISABLE_VULKAN_UNSUPPORTED -> Component.translatable("oculus.warning.vulkan");
         case DISABLE_OPTIFINE_CONFLICT -> Component.translatable("oculus.warning.optifine");
         case DISABLE_PIPELINE_NOT_IMPLEMENTED -> Component.translatable("oculus.warning.pipeline_unavailable");
         case DISABLE_UNKNOWN_BACKEND -> Component.translatable("oculus.warning.unknown");
         case ALLOW_OPENGL_PIPELINE -> null;
      };
      if (detail != null) {
         SystemToast.add(minecraft.gui.toastManager(), BACKEND_WARNING_TOAST, Component.translatable("oculus.warning.title"), detail);
      }
   }

   public ShaderPackRepository.DiscoveryResult discover() {
      try {
         return this.repository.discover();
      } catch (IOException var2) {
         OculusForge.LOGGER.error("Could not scan shader-pack directory {}", this.repository.directory(), var2);
         return new ShaderPackRepository.DiscoveryResult(
            List.of(), List.of(new ShaderPackRepository.DiscoveryIssue(this.repository.directory(), var2.getMessage(), null))
         );
      }
   }

   public void applyDiscovery(ShaderPackRepository.DiscoveryResult result) {
      this.discovery.set(result);
      if (!result.issues().isEmpty()) {
         OculusForge.LOGGER.warn("Shader-pack scan found {} valid pack(s) and {} rejected entry/entries", result.packs().size(), result.issues().size());
         result.issues().forEach(issue -> OculusForge.LOGGER.warn("Rejected shader-pack entry {}: {}", issue.path(), issue.message()));
      }
   }

   public ReloadPreparation prepareReload() {
      ShaderReloadState.ResourceRequest resource = reloadState.beginResources();
      return prepareSelection(resource.selection(), resource.epoch());
   }

   private ReloadPreparation prepareSelection(ShaderReloadState.Request request, long resourceEpoch) {
      if (!reloadState.isCurrent(request)) return new ReloadPreparation(discovery.get(), null, "", request, resourceEpoch);
      ShaderPackRepository.DiscoveryResult result = discover();
      OculusConfig snapshot = request.config();
      if (!snapshot.shadersEnabled() || snapshot.selectedPack().isBlank()) {
         return new ReloadPreparation(result, null, "", request, resourceEpoch);
      }
      ShaderPackDescriptor selected = result.packs().stream()
         .filter(pack -> pack.id().equals(snapshot.selectedPack())).findFirst().orElse(null);
      if (selected == null) {
         return new ReloadPreparation(result, null, "Selected shader pack '" + snapshot.selectedPack() + "' was not found", request, resourceEpoch);
      }
      try {
         return new ReloadPreparation(result, compiler.prepare(selected), "", request, resourceEpoch);
      } catch (Exception failure) {
         OculusForge.LOGGER.warn("Could not prepare shader pack '{}'", selected.displayName(), failure);
         String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
         return new ReloadPreparation(result, null, message, request, resourceEpoch);
      }
   }

   public void applyReload(ReloadPreparation preparation) {
      RenderSystem.assertOnRenderThread();
      if (!reloadState.finishResources(preparation.resourceEpoch())) return;
      compiler.resetPipelineDescriptors();
      graphicsInvalidated = true; // Minecraft may have invalidated cached shader programs.
      PendingSelection waiting = pendingSelection;
      pendingSelection = null;
      try {
         if (waiting != null && reloadState.isCurrent(waiting.preparation().request())) {
            installPreparation(waiting.preparation(), true);
            waiting.completion().complete(null);
         } else {
            if (waiting != null) waiting.completion().complete(null);
            if (reloadState.isCurrent(preparation.request())) installPreparation(preparation, true);
            else pipelineManager.deactivate(); // A newer asynchronous selection will apply when ready.
         }
      } catch (RuntimeException unexpected) {
         graphicsInvalidated = false;
         if (waiting != null && !waiting.completion().isDone()) {
            waiting.completion().completeExceptionally(unexpected);
         }
         pipelineManager.failRuntime(unexpected);
      }
   }

   private void installPreparation(ReloadPreparation preparation, boolean fullResourceReload) {
      applyDiscovery(preparation.discovery());
      lastPreparation = preparation;
      reconcilePipeline(preparation, fullResourceReload || graphicsInvalidated);
      graphicsInvalidated = false;
   }

   private void reconcilePipeline(ReloadPreparation preparation, boolean resourcesInvalidated) {
      RenderSystem.assertOnRenderThread();
      if (!config.shadersEnabled() || decision != BackendPolicy.Decision.ALLOW_OPENGL_PIPELINE) {
         pipelineManager.deactivate();
      } else if (preparation == null) {
         restoreOrReject("Shader-pack resources have not been prepared yet", resourcesInvalidated);
      } else if (!preparation.error().isBlank()) {
         restoreOrReject(preparation.error(), resourcesInvalidated);
      } else if (preparation.program() != null && preparation.program().pack().id().equals(config.selectedPack())) {
         if (pipelineManager.activate(preparation.program())) lastKnownGoodPreparation = preparation.program();
         else restoreOrReject(pipelineManager.lastError(), resourcesInvalidated);
      } else {
         restoreOrReject("Prepared shader pack does not match the current selection", resourcesInvalidated);
      }
   }

   private void restoreOrReject(String message, boolean resourcesInvalidated) {
      if (!resourcesInvalidated && pipelineManager.activePipeline() != null) {
         pipelineManager.keepActive(message); // Shader-only failure did not invalidate the active GPU generation.
      } else if (resourcesInvalidated && lastKnownGoodPreparation != null) {
         pipelineManager.restore(lastKnownGoodPreparation, message);
      } else {
         pipelineManager.reject(message);
      }
   }

   public CompletableFuture<ShaderPackRepository.DiscoveryResult> refreshAsync() {
      return CompletableFuture.supplyAsync(this::discover).thenApply(result -> {
         this.applyDiscovery(result);
         return result;
      });
   }

   public CompletableFuture<Void> applySelection(String packId, boolean enabled) {
      RenderSystem.assertOnRenderThread();
      boolean effectiveEnabled = enabled && !packId.isBlank() && shaderPipelineAvailable();
      ShaderReloadState.Request request = reloadState.select(config.withSelectedPack(packId).withShadersEnabled(effectiveEnabled));
      config = request.config();
      saveConfig();
      lastPreparation = null;
      if (pendingSelection != null) {
         pendingSelection.completion().complete(null);
         pendingSelection = null;
      }
      if (!effectiveEnabled) {
         pipelineManager.deactivate();
         lastKnownGoodPreparation = null;
         selectionFuture = CompletableFuture.completedFuture(null);
         OculusForge.LOGGER.info("Oculus shaders disabled without reloading Minecraft resources");
         return selectionFuture;
      }
      long started = System.nanoTime();
      CompletableFuture<Void> completion = new CompletableFuture<>();
      selectionFuture = completion;
      shaderPreparation.submit(() -> prepareSelection(request, 0)).whenCompleteAsync((prepared, failure) -> {
         if (!reloadState.isCurrent(request)) { completion.complete(null); return; }
         ReloadPreparation result = prepared;
         if (failure != null) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            result = new ReloadPreparation(discovery.get(), null, "Shader preparation failed: " + message, request, 0);
         }
         if (reloadState.resourcesPending()) {
            if (pendingSelection != null) pendingSelection.completion().complete(null);
            pendingSelection = new PendingSelection(result, completion);
            return;
         }
         try {
            installPreparation(result, false);
            OculusForge.LOGGER.info("Shader-only reload '{}' finished in {} ms; no Minecraft asset reload",
               request.config().selectedPack(), (System.nanoTime() - started) / 1_000_000);
            completion.complete(null);
         } catch (RuntimeException unexpected) {
            pipelineManager.failRuntime(unexpected);
            completion.completeExceptionally(unexpected);
         }
      }, Minecraft.getInstance());
      return completion;
   }

   public boolean shaderReloadInProgress() {
      return reloadState.resourcesPending() || !selectionFuture.isDone();
   }

   private void saveConfig() {
      try {
         this.configStore.save(this.config);
      } catch (IOException var2) {
         OculusForge.LOGGER.error("Could not save Oculus configuration to {}", this.configStore.path(), var2);
      }
   }

   public OculusConfig config() {
      return this.config;
   }

   public ShaderPackRepository.DiscoveryResult discovery() {
      return this.discovery.get();
   }

   public GraphicsApi graphicsApi() {
      return this.graphicsApi;
   }

   public String backendName() {
      return this.backendName;
   }

   public BackendPolicy.Decision decision() {
      return this.decision;
   }

   public boolean shaderPipelineAvailable() {
      return this.decision == BackendPolicy.Decision.ALLOW_OPENGL_PIPELINE;
   }

   public boolean shadersActive() {
      return this.config.shadersEnabled() && this.pipelineManager.state() == ShaderPipelineManager.State.READY;
   }

   public String activePackId() {
      return this.pipelineManager.activePackId();
   }

   public ShaderPipelineManager.State pipelineState() {
      return this.pipelineManager.state();
   }

   public String pipelineError() {
      return this.pipelineManager.lastError();
   }

   public OculusCompositePass compositePass() {
      return this.compositePass;
   }

   public int cachedPipelineCount() { return compiler.cachedPipelineCount(); }

   public String diagnosticSummary() {
      String error = pipelineError();
      if (error.length() > 4096) error = error.substring(0, 4096) + "... (truncated)";
      String version = OculusForge.class.getPackage().getImplementationVersion();
      return "Oculus Community Port " + (version == null ? "(development)" : version)
         + "\nScope: experimental terrain/MRT/shadow bridge; not a complete pack-native world renderer"
         + "\nChunk mesh: " + (dev.oculusforge.render.program.TerrainMeshMode.EXTENDED
             ? "experimental 48-byte geometry/state attributes (restart-only)" : "vanilla 28-byte BLOCK layout")
         + "\nHDR world scene: " + (dev.oculusforge.render.world.SceneColorFormat.ENABLED
             ? "experimental ON (restart-only; improved transparency unsupported)" : "OFF (default RGBA8 scene)")
         + "\nJava: " + System.getProperty("java.version") + "\nBackend: " + backendName
         + "\nRequested pack: " + config.selectedPack() + "\nActive pack: " + activePackId()
         + "\nState: " + pipelineState() + "\nReload pending: " + shaderReloadInProgress()
         + "\nOwned pack textures: " + dev.oculusforge.render.program.PackGpuTexture.liveCount()
         + " (" + dev.oculusforge.render.program.PackGpuTexture.liveBytes() + " bytes)"
         + "\nOwned world textures: " + dev.oculusforge.render.world.WorldRenderTargets.liveCount()
         + " (" + dev.oculusforge.render.world.WorldRenderTargets.liveBytes() + " bytes; excludes framegraph scratch targets)"
         + "\nOwned uniform rings: " + (dev.oculusforge.render.program.PackFinalProgram.liveUniformRings()
             + dev.oculusforge.render.world.WorldMatrixBuffer.liveCount())
         + "\nCached pack pipeline descriptors: " + compiler.cachedPipelineCount()
         + "\nDiscovery issues: " + discovery.get().issues().size() + "\nError: " + error
         + "\nReview this text before sharing; errors can contain pack paths.";
   }

   public void close() {
      RenderSystem.assertOnRenderThread();
      reloadState.close();
      shaderPreparation.close();
      selectionFuture.cancel(false);
      if (pendingSelection != null) pendingSelection.completion().cancel(false);
      pendingSelection = null;
      lastPreparation = null;
      lastKnownGoodPreparation = null;
      this.pipelineManager.close();
      compiler.resetPipelineDescriptors();
      OculusForge.LOGGER.info("Oculus shutdown: ownedTextures={} ownedBytes={} uniformRings={} worldTextures={} worldBytes={}",
         dev.oculusforge.render.program.PackGpuTexture.liveCount(), dev.oculusforge.render.program.PackGpuTexture.liveBytes(),
         dev.oculusforge.render.program.PackFinalProgram.liveUniformRings() + dev.oculusforge.render.world.WorldMatrixBuffer.liveCount(),
         dev.oculusforge.render.world.WorldRenderTargets.liveCount(), dev.oculusforge.render.world.WorldRenderTargets.liveBytes());
   }

   private record PendingSelection(ReloadPreparation preparation, CompletableFuture<Void> completion) {}

   public static record ReloadPreparation(ShaderPackRepository.DiscoveryResult discovery,
      PreparedPackPipeline program, String error, ShaderReloadState.Request request, long resourceEpoch) {}

}
