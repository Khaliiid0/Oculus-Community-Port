package dev.oculusforge.client.gui;

import dev.oculusforge.client.OculusClientService;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ShaderPackScreen extends Screen {
   private final Screen parent;
   private final OculusClientService service;
   private String selectedPack;
   private boolean shadersEnabled;
   private boolean rendererEnabled;
   private MultiLineLabel status = MultiLineLabel.EMPTY;
   private String lastStatusText = "";

   public ShaderPackScreen(Screen parent, OculusClientService service) {
      super(Component.translatable("oculus.screen.title"));
      this.parent = parent;
      this.service = service;
      this.selectedPack = service.config().selectedPack();
      this.shadersEnabled = service.config().shadersEnabled();
      this.rendererEnabled = service.rendererPreference();
   }

   protected void init() {
      super.init();
      List<String> choices = new ArrayList<>();
      choices.add("");
      choices.addAll(this.service.discovery().packs().stream().map(ShaderPackDescriptor::id).toList());
      if (!this.selectedPack.isBlank() && !choices.contains(this.selectedPack)) {
         this.selectedPack = "";
      }

      CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(this.shadersEnabled)
         .create((this.width - 310) / 2, 102, 310, 20, Component.translatable("oculus.screen.shaders"), (button, value) -> this.shadersEnabled = value);
      enabledButton.active = this.service.shaderPipelineAvailable() && !this.selectedPack.isBlank()
         && !dev.oculusforge.config.RendererBootstrap.ACTIVE.restartRequired(rendererEnabled);
      CycleButton<String> packButton = CycleButton.builder(ShaderPackScreen::packLabel, this.selectedPack)
         .withValues(choices)
         .create((this.width - 310) / 2, 76, 310, 20, Component.translatable("oculus.screen.pack"), (button, value) -> {
            this.selectedPack = value;
            enabledButton.active = this.service.shaderPipelineAvailable() && !this.selectedPack.isBlank()
               && !dev.oculusforge.config.RendererBootstrap.ACTIVE.restartRequired(rendererEnabled);
         });
      this.addRenderableWidget(packButton);
      this.addRenderableWidget(enabledButton);
      var rendererButton = CycleButton.onOffBuilder(rendererEnabled)
         .create((this.width - 310) / 2, 128, 310, 20, Component.literal("World renderer (restart required)"), (button,value) -> {
            rendererEnabled = value;
            button.active = service.shaderPipelineAvailable() || value;
            enabledButton.active = service.shaderPipelineAvailable() && !selectedPack.isBlank()
               && !dev.oculusforge.config.RendererBootstrap.ACTIVE.restartRequired(value);
         });
      rendererButton.active = service.shaderPipelineAvailable() || rendererEnabled;
      this.addRenderableWidget(rendererButton);
      this.addRenderableWidget(Button.builder(Component.translatable("oculus.screen.refresh"), button -> {
         button.active = false;
         this.service.refreshAsync().whenCompleteAsync((ignored, error) -> this.refreshPackWidgets(), this.screenExecutor);
      }).bounds(this.width / 2 - 154, this.height - 26, 100, 20).build());
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("oculus.screen.save"),
               button -> {
                  button.active = false;
                  if (!service.saveRendererPreference(rendererEnabled)) {
                     refreshPackWidgets();
                     return;
                  }
                  if (dev.oculusforge.config.RendererBootstrap.ACTIVE.restartRequired(rendererEnabled)) shadersEnabled = false;
                  this.service
                     .applySelection(this.selectedPack, this.shadersEnabled)
                     .whenCompleteAsync((ignored, error) -> this.refreshPackWidgets(), this.screenExecutor);
               }
            )
            .bounds(this.width / 2 - 50, this.height - 26, 100, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.gui.setScreen(this.parent))
            .bounds(this.width / 2 + 54, this.height - 26, 100, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.translatable("oculus.screen.copy_status"), button -> {
         this.minecraft.keyboardHandler.setClipboard(this.service.diagnosticSummary());
         button.setMessage(Component.translatable("oculus.screen.copied"));
      }).bounds((this.width - 310) / 2, this.height - 52, 310, 20).build());
      lastStatusText = "";
      updateStatus();
   }

   @Override
   public void tick() {
      super.tick();
      updateStatus();
   }

   private void updateStatus() {
      Component text = statusComponent();
      String plain = text.getString();
      if (!plain.equals(lastStatusText)) {
         lastStatusText = plain;
         this.status = MultiLineLabel.create(this.font, Math.min(this.width - 40, 520),
            Math.max(1, (this.height - 222) / 11), text);
      }
   }

   private void refreshPackWidgets() {
      this.clearWidgets();
      this.init();
   }

   private static Component packLabel(String pack) {
      return pack.isBlank() ? Component.translatable("oculus.screen.disabled") : Component.literal(pack);
   }

   private Component statusComponent() {
      Component backend = Component.translatable("oculus.screen.backend", new Object[]{this.service.backendName()});
      String activePack = this.service.activePackId();
      if (service.shaderReloadInProgress()) return CommonComponents.joinForNarration(
         backend, Component.translatable("oculus.screen.loading"));
      if (!service.pipelineCompatibilityWarning().isEmpty()) return CommonComponents.joinForNarration(
         Component.literal(service.pipelineCompatibilityWarning()), backend,
         Component.literal(service.rendererModeStatus(rendererEnabled)));
      String error = this.service.pipelineError();
      if (error.length() > 400) error = error.substring(0, 400) + "...";

      Component decisionText = switch (this.service.decision()) {
         case ALLOW_OPENGL_PIPELINE -> !this.service.pipelineError().isBlank()
         ? (
            activePack.isBlank()
               ? Component.translatable("oculus.screen.status.error", new Object[]{error})
               : Component.translatable("oculus.screen.status.degraded", new Object[]{activePack, error})
         )
         : (
            this.service.shadersActive()
               ? Component.translatable("oculus.screen.status.active", new Object[]{activePack})
               : Component.translatable("oculus.screen.status.ready")
         );
         case DISABLE_VULKAN_UNSUPPORTED -> Component.translatable("oculus.screen.status.vulkan");
         case DISABLE_UNKNOWN_BACKEND -> Component.translatable("oculus.screen.status.unknown");
         case DISABLE_OPTIFINE_CONFLICT -> Component.translatable("oculus.warning.optifine");
         case DISABLE_PIPELINE_NOT_IMPLEMENTED -> Component.translatable("oculus.screen.status.pipeline_unavailable");
      };
      return CommonComponents.joinForNarration(Component.literal(service.rendererModeStatus(rendererEnabled)), backend, decisionText,
         service.discovery().issues().isEmpty() ? Component.empty()
            : Component.translatable("oculus.screen.rejected", service.discovery().issues().size()));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.centeredText(this.font, this.title, this.width / 2, 28, -1);
      ActiveTextCollector text = graphics.textRenderer();
      this.status.visitLines(TextAlignment.CENTER, this.width / 2, 162, 11, text);

   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
