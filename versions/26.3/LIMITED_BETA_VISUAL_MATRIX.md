# Limited-beta visual matrix — execution checklist

Status: bounded observations recorded; remaining release work is still open.
This is NOT a list of fully supported shader packs.
Target the corrected marked checkpoint 3 packaged JAR, production Forge 66.1.2,
Minecraft 26.3, Java 25, AMD integrated OpenGL and classic transparency. Keep every
test tied to an exact pack ZIP hash, selected option snapshot and resource context.

Installed checkpoint 3 follow-up:
[water/material observations](INSTALLED_VISUAL_MATRIX_2026_11_17.md).
The original-pack default water views, F3+T and fullscreen/windowed recovery are
observed on the packaged binary. Both bounded LabPBR settings also show the
numeric material pattern and visible submerged scenes; BSL rain/clear is observed.
The [natural-shore lighting follow-up](LIGHTING_SHORE_2026_11_18.md) records
BSL/Complementary day, sunset, night, rain and clear views. A bright Complementary
bank/cave discrepancy remains under review. The
[in-pass follow-up](DEFERRED_INPUT_DIAGNOSTIC_2026_11_18.md) validates bounded
live/opaque depth and identifies the deferred border-fog path, rather than proving
a broken-depth/cloud bug. No global fog bypass is a proposed fix. PBR optical
comparisons and timed gameplay qualification remain
separate work; the positive user gameplay report has unknown active duration.
These observations do not close the full checklist below.

## Bounded settings to qualify

| Original pack/settings | Existing evidence | Packaged visual checks still needed |
| --- | --- | --- |
| BSL v10.1.1, ZIP defaults, no resource pack | Checkpoint 2 activation/daylight/reload smoke passes, but its user gameplay test was interrupted. Checkpoint 3 compatibility pause/recovery passes. | Corrected-binary gameplay soak; above/underwater transitions and shore boundaries; day/night/weather colour sanity; no stale attachments after reload/resize. |
| BSL v10.1.1, `ADVANCED_MATERIALS=true`, `MATERIAL_FORMAT=1`, LabPBR 1.3 resource context | Source-client numeric normal/specular and smoothed wetness tests pass, including default rain reflections. | Repeat static material/quadrant distinction and water/material observations with the installed JAR; confirm rain/clear recovery. |
| Complementary Reimagined r5.7.1, ZIP defaults, no resource pack | Installed activation, daylight scene, Nether/End/Overworld travel and logout/re-entry pass. | Water edges/underwater transitions; weather/colour sanity and reload/resize observation. |
| Complementary Reimagined r5.7.1, `RP_MODE=3`, LabPBR 1.3 resource context | Source-client numeric normal/specular GPU and source-lifecycle tests pass. | Repeat static material distinction and visible water/material behavior with the installed JAR, then return to default resource context. |

"Defaults" means unchanged declarations in the exact ZIP, not an assumed named
LOW/HIGH profile. The current option path is bounded explicit sidecar selection,
not full profile inheritance or a complete Iris settings GUI. Do not promise
every quality preset/option because a few declared material switches work.

BSL SHA-256: `131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668`

Complementary SHA-256: `24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0`

## Acceptance and recording

- Capture original F2 images with the exact setting/resource context, scene,
  dimension and test action recorded. Do not replace a faulty view with a cropped
  or edited promotional screenshot.
- Distinguish CPU/ShaderC success, actual native admission, target formats and
  visible GPU behavior. No single one proves all the others.
- Check for a global green cast, left-edge vertical lines, black/frozen scenes,
  stale content, missing opaque/cutout terrain and obvious water-boundary seams.
- A successful compiler or lifecycle smoke is not image parity with Iris/OptiFine.
  Unsupported optical paths must remain explicit; do not mark them as fixed.
- Keep entity/hand native gbuffer parity, animated materials, arbitrary POM and
  every refraction setting outside this limited beta's claims.
- Restore option sidecars, resource context and renderer preference after testing;
  preserve original pack ZIPs and previous evidence. No public upload until the
  remaining gates are explicitly reviewed.

The reproducible `minecraft:end_of_frame` warning is tracked in
POST_EFFECT_BASELINE_2026_11_17.md. It also occurs without Oculus; this checklist
does not require masking it or adding a dummy post chain to make logs look clean.
