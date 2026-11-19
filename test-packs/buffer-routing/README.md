# Buffer-routing diagnostic pack

Authored for this repository; distributed under the repository's LGPL-3.0-only license.

This is a test fixture, not a visual enhancement pack. Copy this directory into an isolated
Minecraft instance's shaderpacks directory and select buffer-routing.

The intrusive stripe is now **off by default**. For ordinary visual acceptance checks,
use the separate buffer-roundtrip fixture, which exercises independent buffers while
preserving the full scene. Never leave a diagnostic pack selected after testing.

Only when deliberately debugging routing, change OCULUS_ROUTING_OVERLAY to 1 in
shaders/debug.glsl. Explain the overlay before displaying it, then restore it to 0.
In this opt-in mode, a solid green stripe covers the left 15% of the world view; the rest
remains the normal world. HUD and menus are drawn separately. A red stripe means the
auxiliary attachment was incorrectly routed. deferred writes a marker into colortex3;
composite copies the original scene into colortex0; final reads BOTH attachments,
including the legacy alias composite for colortex3. Three independent scratch targets
are expected. This catches the old bug that substituted the scene for every color sampler.

This fixture does not test shadows, terrain shaders, performance or long-session stability.
