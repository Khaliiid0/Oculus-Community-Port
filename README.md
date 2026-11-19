# Oculus Community Port

## Minecraft 26.3 — 0.4.0-beta.4 source branch

For the 26.3 limited beta, use [the module build guide](versions/26.3/SOURCE_BUILD.md)
and [scope/acceptance](versions/26.3/PUBLIC_BETA_ACCEPTANCE.md). Java 25 and Forge
66.1.2 are the qualified target. Keep both shared `src/` and `versions/26.3/`.
This is not complete Iris parity; world rendering is opt-in and restart-required.
Final binary/source qualification is separate from build acceptance. The older
target/development notes below are historical and do not describe this new file.

Unofficial, client-only Forge shader project for Minecraft **1.21.11** and **26.2**.
Licensed under LGPL-3.0-only; not an official Oculus, Iris, Forge or Mojang release.

Historical 26.3 source-only milestone, superseded by the acceptance above:
the [wetness lifecycle milestone](versions/26.3/WETNESS_LIFECYCLE_QUALIFICATION_2026_11_16.md)
passes scoped post-only/dimension-reset checks and 1,111 tests in each build mode.
This does not supply a 26.3 release or change the preserved 26.2 candidates.

## Working tree: world-renderer development — not release-qualified

Prepared milestone: **0.4.0-beta.3 / Forge 26.2**, not published. See
[candidate materials](release-notes/0.4.0-beta.3/README.md) and
[qualification / publication hold](release-notes/0.4.0-beta.3/QUALIFICATION.md).
This is separate from the preserved beta.2 post-processing candidate.

The current 26.2 sources include experimental terrain, MRT gbuffer and terrain-shadow
integration. With the development extended-terrain flag, BSL 10.1.1 executes native
terrain/water/shadow GLSL and has scoped Overworld GPU checks. Complementary now
GPU-compiles Overworld/Nether/End terrain/water/shadows, activates 27 post programs,
and submits native Overworld terrain after binding real skyColor.
Complete material paths, broadly qualified world-scene capture and other essential
rendering semantics are still missing. **Do not upload a
fresh build from this tree as a complete BSL/Complementary-compatible release.**
The 1.21.11 branch remains post-processing-only and rejects world-renderer packs.

Current continuation: [opt-in HDR world-scene capture](HDR_WORLD_SCENE_PROGRESS.md),
October 28. 819 tests pass in each of three actual mesh/HDR configurations.
An owned, declared-format colortex0 now preserves native scene precision into
post-processing, with unchanged vanilla target identities and borrowed depth.
Typed presentation, bounded descriptor caching and frame-lifetime guards are
included. HDR remains a restart-only development flag, off by default; full
transparency/material/world-renderer compatibility and release gates remain open.
See the checkpoint for final-artifact GPU evidence and failure history.

Earlier continuation: [shared native sky color](NATIVE_SKY_COLOR_PROGRESS.md),
October 27. 800 tests pass in both actual mesh modes. Native terrain/water/shadows
and post stages share an immutable camera-environment sky RGB, independent of
whether vanilla draws a sky. The UBO appends 16 bytes without shifting old fields.
GPU nonzero parity, fault/recovery, reload/resize and real day/night RGB changes
pass. The final JAR activates BSL/Complementary, submits native Overworld terrain/
shadows, and returns all five tracked allocation counters to zero at shutdown.
Actual-pack results and remaining gates are documented in the checkpoint.
These are scoped development checks, not full visual compatibility or release qualification.

Earlier continuation: [static resource-pack specular atlas](NATIVE_SPECULAR_PROGRESS.md),
October 26–27. 789 tests pass in both actual mesh modes. Real static `_s.png` maps
now use live block-atlas coordinates, RGBA and material-aware mipmaps; a verified
absent-map set uses Iris's four-byte default. Packaged-JAR GPU real-UV terrain/
shadow palette, alpha, top-mip, fault/recovery, rejection/recovery and resize checks
pass. Native sampler admission now counts inherited terrain slots and vertex
material lookups, rejecting over-capacity programs before GPU activation and
checking actual device limits. Animated/resized/unsupported metadata maps explicitly reject. Complementary
Overworld now reaches skyColor and still uses internal terrain; full compatibility
and release qualification remain open. See the checkpoint for evidence and limits.

Earlier continuation: [shared environmental fog ranges](NATIVE_FOG_RANGES_PROGRESS.md),
October 25–26. 766 tests pass in both actual mesh modes. Native and fullscreen fog
members now share real post-camera environmental ranges and RGBA alpha without
growing the UBO. In-world exact nonzero fog, mismatch/recovery and reload/resize
checks pass on the final JAR. A real Complementary GPU error also exposed
an unrelated blockEntityId scope-rewrite bug; four failing regression tests now
pass with scope-aware replacement. The rebuilt artifact compiles Complementary's
newly eligible Nether/End native programs and activates 27 post programs; travel
and full reloads confirm native terrain/shadow submission in both dimensions.
BSL switching, shaders-off and zero tracked shutdown allocations pass. Full visual
compatibility and release qualification remain open; see the checkpoint for
per-binary evidence and remaining work.

Earlier continuation: [real Darkness pulse and fullscreen cloud fallback](DARKNESS_LIGHT_PROGRESS.md),
October 24–25. 742 tests pass in both actual mesh modes. The lightmap's real pulse
now replaces an incorrect post alias and feeds native/post stages. Final-JAR GPU
nonzero/fault/recovery, reload/resize and BSL regression pass. A real Complementary
vertex cloudHeight redefinition was found and fixed; its 27 post programs now
activate, but terrain still uses internal fallbacks for missing material/fog/sky
paths. Shutdown allocations reach zero. Interrupted overnight runtime is not a
controlled soak, and this remains not release-ready.

Earlier continuation: [live atlas size and held-item IDs](ATLAS_HELD_INPUTS_PROGRESS.md),
October 23. 727 tests pass in both actual mesh modes. Pack-local item/model IDs
and live block-atlas dimensions now reach native terrain/shadow and post shaders.
Packaged-JAR GPU exact IDs, hand swaps, model overrides, air mapping, fault/recovery,
reload/resize, BSL switch and tracked cleanup pass. Complementary Nether/End now
reach darknessLightFactor; Overworld specular and sky remain blocked. Not release-ready.

Earlier continuation: [opaque-depth helper argument positions](DEPTH_HELPER_ARGUMENTS_PROGRESS.md),
October 23. 712 tests pass in both actual mesh modes. Validated single-sampler
leaf helpers can receive depth at argument seven or other positions without
changing ordinary texture callers. Final-JAR GPU positive/mismatch/recovery and
reload/resize checks pass; BSL switches/compiles, but its bright water scene is
not image-quality-qualified. Complementary water now reaches later specular/
held-item/atlas blockers; release gates remain open.

Earlier continuation: [block model layer routing](BLOCK_LAYER_ROUTING_PROGRESS.md),
October 23. 698 tests pass in both actual mesh modes. Explicit material-layer
overrides now route model quads into actual chunk meshes, pin tables per section
build and trigger rebuilds when changed/disabled. Scoped GPU no-override control,
switch recovery, reload/resize, shaders-off and BSL regression pass. Complementary
now prepares on CPU but still uses internal terrain fallbacks for missing native
features; it is not fully supported or release-qualified.

Earlier continuation: [shared environment expressions](SHARED_ENVIRONMENT_INPUTS_PROGRESS.md),
October 22. 680 tests pass in both actual mesh modes. Shared custom expressions now
use real rain, raw eye light, frame time and precipitation, preserve their smoothing,
and reconcile the exact native effect identity. Cloud fallback literals keep the
real host binding. Scoped nonzero GPU parity/fault/recovery, reload/resize and BSL
switching pass. Complementary reaches the next material-layer override blocker;
it remains unsupported and release gates remain open.

Earlier continuation: [real cloud height and property branches](NATIVE_CLOUD_HEIGHT_PROGRESS.md),
October 22. 658 tests pass in both actual mesh modes. Camera-extracted cloud height
is shared by native/post; inactive macOS property directives no longer discard
Complementary's custom table on Windows. Scoped GPU cloud parity/fault/recovery,
reload/resize and BSL switching pass. Complementary still rejects on shared custom
expression conflicts/inputs; release gates remain open.

Earlier continuation: [native stage interfaces](NATIVE_STAGE_INTERFACE_PROGRESS.md),
October 22. 648 tests pass in both actual mesh modes. Native modern/legacy stage
interfaces now match types/interpolation; valid function parameters are no longer
mistaken for mesh inputs. Scoped GPU value-transfer/failure/recovery, reload/resize
and BSL switch checks pass. Complementary still needs real cloud/custom inputs;
release gates remain open.

Earlier continuation: [validated native helper pruning](NATIVE_HELPER_PRUNING_PROGRESS.md),
October 22. 631 tests pass in both actual mesh modes. Unreachable library helpers
no longer falsely require native texture/custom inputs. Scoped GPU positive,
deliberate-mismatch/recovery, reload/resize, BSL switch and shaders-off checks pass.
Complementary still rejects on remaining inputs/interfaces; release gates stay open.

Earlier continuation: [shared previous-camera position](SHARED_CAMERA_HISTORY_PROGRESS.md),
October 22. 620 tests pass in both actual mesh modes. Native and post stages share
one previous-position snapshot. Scoped GPU checks cover prior-frame markers,
camera jumps, latched failure/recovery, dimension transitions and resize.
Complementary still needs other inputs/interfaces; this is not release qualification.

Earlier continuation: [native scene inputs](NATIVE_SCENE_INPUTS_PROGRESS.md),
October 22. 608 tests pass in both actual mesh modes. Native brightness, celestial
angle and effect inputs pass scoped GPU parity/failure-control, night/effect,
reload and resize checks. Unreferenced shared declarations no longer falsely
require bindings; active missing inputs still reject. Complementary remains
unsupported; release gates are open.

Earlier continuation: [two independent shadow colors](SHADOW_COLOR_MRT_PROGRESS.md),
October 21. 596 tests pass in both actual mesh modes. Controlled GPU checks cover
native/post readers, reversed MRT order, isolated alias failures, reload/resize and
empty-frame clears. Complementary still needs native uniform bindings and broader
renderer work; this is not release qualification.

Earlier continuation: [texture capacity and sampler identity](TEXTURE_CAPACITY_PROGRESS.md),
October 21. 590 tests pass in both actual mesh modes; sixteen-texture GPU failure/
recovery checks, BSL live switching/reload, shaders-off and tracked cleanup pass. Complete pack support and
release gates remain open. The entries below are historical checkpoints.

Earlier continuation: [native shadow-color reads](NATIVE_SHADOW_COLOR_PROGRESS.md), October 2.
296 tests pass. Terrain can bind completed native shadowcolor0, with producer/readiness checks
and empty-frame clearing. This new path is not GPU-verified. BSL now first reaches held-light
uniforms in all three dimensions; its shadow program and other renderer features remain blocked.

Previous continuation: [native noise GPU checkpoint](NATIVE_NOISE_GPU_PROGRESS.md), October 2.
291 tests pass. Scoped Radeon checks cover known PNG pixels in terrain/shadow/post, independent
mismatch detection, generated native-only noise, reload recovery, resize and tracked cleanup.
BSL still needs shadowcolor0 and held-light bindings, among other missing renderer features.

Previous continuation: [relative-eye GPU checkpoint](NATIVE_EYE_GPU_PROGRESS.md), October 1.
281 tests pass, plus scoped Radeon first/rear/front-camera, negative-control, reload,
resize and cleanup checks. BSL terrain preparation reaches the next missing sampler, noisetex.
This does not establish full artist-pack support or exhaustive camera-state correctness.

Previous continuation: native terrain/shadow can lower a small validated subset of pack-defined
clock expressions, including BSL's timeAngle and framemod definitions. The full build passes
276 tests. [NATIVE_CUSTOM_UNIFORMS_GPU_PROGRESS.md](NATIVE_CUSTOM_UNIFORMS_GPU_PROGRESS.md)
records scoped Radeon checks, including a wrong-divisor negative control, alternate divisor,
reload, resize and tracked cleanup. This is not full shader-pack compatibility. See
[NATIVE_CUSTOM_UNIFORMS_PROGRESS.md](NATIVE_CUSTOM_UNIFORMS_PROGRESS.md) for implementation limits.

The September 30 continuation replaces per-program animation counters with a shared native/post
clock and adds native frameCounter/frameTime/frameTimeCounter. The full build passes 266 tests.
[ANIMATION_CLOCK_GPU_PROGRESS.md](ANIMATION_CLOCK_GPU_PROGRESS.md) records scoped Radeon checks
for native/post counter agreement, deliberate mismatch detection/recovery, pause-menu clock
advancement, reload, resize and tracked cleanup. This is feature validation, not release
qualification. [ANIMATION_CLOCK_PROGRESS.md](ANIMATION_CLOCK_PROGRESS.md) describes implementation.

The September 29 continuation adds native worldDay and named, bounded missing-binding
diagnostics. [NATIVE_DAY_PROGRESS.md](NATIVE_DAY_PROGRESS.md) records the implementation
and updated pack audit. [NATIVE_DAY_GPU_PROGRESS.md](NATIVE_DAY_GPU_PROGRESS.md) records
257 passing tests and Radeon checks for exact day values, mismatch detection/recovery,
a natural day rollover, reload, resize and tracked resource cleanup. These are scoped
diagnostic checks, not full pack compatibility or release qualification.

The September 28 continuation adds native worldTime and corrects the 26.2 post-processing
worldTime/worldDay source to use the synchronized Overworld clock rather than elapsed game age.
[NATIVE_CLOCK_PROGRESS.md](NATIVE_CLOCK_PROGRESS.md) describes the implementation.
[NATIVE_CLOCK_GPU_PROGRESS.md](NATIVE_CLOCK_GPU_PROGRESS.md) records 251 passing tests and
scoped paused-clock, day-boundary jump, one-tick mismatch, reload and Nether GPU checks.
The follow-up also verifies a resumed/advancing clock, reload while running and Nether/End
return trips. Multiplayer and long-soak checks remain pending; this is not release qualification.

The September 25 continuation adds main-viewport dimensions to native terrain/shadow uniforms.
[NATIVE_VIEWPORT_PROGRESS.md](NATIVE_VIEWPORT_PROGRESS.md) describes the implementation.
[NATIVE_VIEWPORT_GPU_PROGRESS.md](NATIVE_VIEWPORT_GPU_PROGRESS.md) records 245 passing root
tests and Radeon GPU checks for resize, reload, deliberate wrong-size detection and recovery,
and clean shutdown. This validates the viewport subset, not full shader-pack compatibility.

The September 24 continuation adds bounded native texture-LOD/gradient translation.
[NATIVE_EXTENSIONS_PROGRESS.md](NATIVE_EXTENSIONS_PROGRESS.md) records 239 root and 109 child
tests, Radeon GPU resize/reload/shutdown checks, and remaining BSL/Complementary blockers.
The preceding [declaration checkpoint](NATIVE_DECLARATIONS_PROGRESS.md) added grouped native
shader declarations and stricter cross-stage varying validation.

The preceding continuation added current-frame camera/shadow matrices and inverses to the native
terrain/shadow binding path, with a bounded shared uniform ring and recovery from the initial
zero-FOV camera frame. See [WORLD_MATRICES_PROGRESS.md](WORLD_MATRICES_PROGRESS.md) for the
September 16–17 GPU checks and remaining semantic gaps. This is not complete Iris matrix support.

The preceding continuation added bounded block.properties material mapping to the test-only
chunk normals, tangents/handedness and mid-UV path. Its session-fixed 48-byte layout is disabled
by default and increases chunk vertex storage by 71.4%. A pack-owned lookup supplies material
IDs to native terrain and shadow programs without baking pack IDs into meshes. This remains
a limited experimental subset, not full shader compatibility. See
[MATERIAL_IDS_PROGRESS.md](MATERIAL_IDS_PROGRESS.md) for verification and remaining limits.
The earlier 44-byte geometry checkpoint is preserved in
[MESH_ATTRIBUTES_PROGRESS.md](MESH_ATTRIBUTES_PROGRESS.md).
The preceding [native shader checkpoint](NATIVE_SHADER_PROGRESS.md) added a limited native
compiler, bounded world pipeline caching, and a format-metadata compilation fix.
The previous auxiliary history/direct-world-read work puts Complementary's planned 1080p scratch
storage at 87 MiB (previously 142 MiB). This is not full temporal-rendering compatibility. See
[BUFFER_HISTORY_PROGRESS.md](BUFFER_HISTORY_PROGRESS.md) for the history checkpoint,
[WORLD_BUFFER_PROGRESS.md](WORLD_BUFFER_PROGRESS.md) for the preceding direct-read optimization,
[HDR_FORMAT_PROGRESS.md](HDR_FORMAT_PROGRESS.md) for the preceding typed-buffer work,
and the earlier [world-renderer checkpoint](WORLD_RENDERER_PROGRESS.md).
Existing `candidates/0.4.0-beta.2` files have not been replaced. The working-tree version
string still matches that old candidate; identify development tests by their recorded SHA-256,
not by filename alone.

## Previously verified post-processing candidate: 0.4.0-beta.2

This beta adds shader-only switching, bounded PNG pack textures, pipeline-cache reuse,
stricter configuration/directory/source memory bounds, and extensive failure/lifecycle checks
on top of the previous color-buffer, noise-texture, and atomic-presentation improvements.
It is an uploadable beta for the documented post-processing subset, **not a complete Iris/Oculus
world renderer or general shader-pack replacement**. BSL v10.1.1 and Complementary Reimagined r5.7.1 are
explicitly unsupported; a successful final-pass load in the older beta did not mean the
full pack rendered correctly.

See the [beta validation report](BETA_0.4.0.md), the
[final preview report](PREVIEW_0.4.0_3.md), and [known limitations](KNOWN_ISSUES.md).

| Target | Forge | JDK | Source |
|---|---|---|---|
| Minecraft 26.2 | 65.1.0 | Java 25 | Repository root |
| Minecraft 1.21.11 | 61.2.0 | Java 21 | [versions/1.21.11](versions/1.21.11) |

The previously published `0.3.0-beta.1` source is preserved at
[revision b26e55a](https://github.com/Khaliiid0/Oculus-Community-Port/tree/b26e55a).
The working tree has since moved beyond that candidate; neither historical report certifies new builds.

## Implemented in the previous post-processing beta

- Separate current-frame RGBA8 color buffers, with correct `colortex` and supported legacy alias routing.
- Single-output `DRAWBUFFERS` / `RENDERTARGETS` directives for deferred/composite stages.
- Offscreen stage execution, then one final copy to the scene after all stages succeed.
- Version-aware scratch-target reuse after the last reader, with a 128 MiB scratch-color safety budget.
- Real generated RGB white-noise `noisetex`, repeat/linear sampling, and bounded
  `noiseTextureResolution` (default 256; integer literals 1–1024 after preprocessing).
- Separate root, Overworld, Nether and End program variants, including every supported stage.
- Preprocessed custom vertex and fragment sources.
- Pack-local 2D PNG textures: named samplers, deferred/composite color overrides, and custom noisetex.
  Images are dimension/read/memory bounded; optional blur/clamp metadata is supported.
- Shader-only asynchronous switching without reloading Minecraft assets; one running preparation
  and at most one queued replacement, with newest-request wins and full-resource-reload coordination.
- Identical shader sources reuse the same pipeline descriptors across switches; a 512-descriptor
  safety cap asks for F3+T instead of allowing unbounded growth.
- Immediate disable/cleanup, last-known-good failure recovery, and a copyable diagnostic summary.
- Explicit rejection of unsupported world/shadow/compute programs, MRT, history, and render-state overrides.
- No invented Iris, AMD, normal-map or specular-map capability macros.
- Existing bounded ZIP/directory loading, secure includes, asynchronous preparation, common uniforms,
  atomic configuration writes, and last-known-good reload recovery.
- Bounded configuration reads, portable pack-name validation, cumulative directory-pack read limits,
  and a 32-million-character aggregate prepared-source ceiling.

These changes prioritize correctness and recovery. Extra scratch buffers and the final copy
can cost GPU memory/bandwidth; no FPS improvement is claimed.

## Build the development tree

Use the matching JDK and run from the appropriate source directory:

```powershell
.\gradlew.bat build --warning-mode=fail
```

On Linux/macOS use `./gradlew` instead (not graphics-tested in this Windows validation).
The task writes development binaries and sources under `build/libs/`; it does not
alter `release/` or `candidates/`. Do not run `packageCandidate` on this tree until
the version and release gates have been updated.

After building both targets, run `tools/Verify-Candidate.ps1` from PowerShell to check checksums,
packaging boundaries, Java class targets, and corresponding source contents.

Both targets use Gradle 9.6.0 and ForgeGradle 7.0.31. Configuration-cache problems now fail
the build rather than being accepted as warnings.

## Historical candidate validation

Both targets pass 106 automated tests. The suite covers 1,000 generated routing chains in one reference-simulation test,
bounded PNG decoding and texture bindings, reload races, queue coalescing, descriptor reuse,
noise generation, configuration/source budgets, and real visual-fixture preparation.
The 26.2 beta passed an extended 308-check live run plus world unload/reload; 1.21.11
passed the 68-check live sequence. Exact results are in the beta report.
Use the three-stage [normal-view round-trip fixture](test-packs/buffer-roundtrip) for visual
acceptance. It exercises independent color buffers without a stripe or split screen. The
[routing diagnostic](test-packs/buffer-routing) now requires explicit opt-in for its colored
overlay. The [noise fixture](test-packs/noise-preview) adds subtle full-screen grain, not an overlay.
The [PNG round-trip fixture](test-packs/custom-texture-roundtrip) preserves the normal scene
while exercising custom bindings and view-size uniform buffers.
The opt-in [live lifecycle probe](tools/LIFECYCLE_TEST.md) checks switching, failure recovery,
full reloads, overlapping requests, and owned resources. Its classes are blocked from release JARs.
Test fixtures must not remain selected after a session. These fixtures are not benchmarks.

See [the visual-test correction](VISUAL_TEST_FOLLOWUP.md) for the stripe's cause,
the unobstructed live checks, and the saved-configuration cleanup.

Historical beta reports are retained in `TEST_REPORT.md` and
`versions/1.21.11/TEST_REPORT.md`; they are **not** evidence of complete BSL/Complementary compatibility.
Current scope and remaining release gates are in [RELEASE_GATES.md](RELEASE_GATES.md).

## License, sources and attribution

See [LICENSE](LICENSE), [LICENSE_NOTES.md](LICENSE_NOTES.md), and [NOTICE](NOTICE).
The bundled, relocated JCPP 1.4.14 dependency is Apache-2.0 licensed and its license is included.

- [Original Oculus](https://github.com/Asek3/Oculus)
- [Iris](https://github.com/IrisShaders/Iris)
- [Community-port issue tracker](https://github.com/Khaliiid0/Oculus-Community-Port/issues)

Publish the exact corresponding source with any future binary release. Do not direct community-port
bugs to the original Oculus, Iris, or shader-pack developers.
