# Architecture

## Status boundary

The current sources implement Forge 1.21.11 and 26.2 full-screen shader pipelines,
plus an experimental 26.2 native terrain/MRT/shadow bridge. They do not implement a
complete Oculus/Iris world renderer. 1.21.11 remains post-only and rejects world programs.

26.2 also has a restart-only development flag, `oculus.experimental.hdrWorldScene`
(Gradle `-PoculusHdrWorldScene=true`, off by default). A thread-local scope wraps
LevelRenderer.render and routes only the original main target's color getters to
an owned colortex0 texture in the pack-declared format. Minecraft's RenderTarget
OBJECT and depth storage remain unchanged, including long-lived sky-renderer
references. This is important: returning a frame-local replacement target from
GameRenderer.mainRenderTarget lets lazy vanilla renderers cache a stale attachment
across resize. SceneColorBindings validates each frame's storage without changing
those cached object identities.

RenderPassMixin adapts pipeline descriptors only for actual attachments identified
as that owned scene color. ScenePipelineFormats preserves shaders, bindings, vertex
layout, blending, depth/stencil, topology and other targets; its identity cache is
bounded to 256 descriptors and resets on full resource reload. Auxiliary, shadow,
outline and GUI targets are not globally reformatted. The original depth attachment
is borrowed without allocation/copy. WorldTargetLayout charges owned scene color
against the existing 192 MiB world-texture limit.

Post colortex0 reads that HDR storage directly. Typed fullscreen transfers handle
the final post result and presentation back to vanilla RGBA8. The scope is cleared
before resolving that destination and always unwound; a frame lease defers owner
close until presentation ends. This does not invent tone mapping or move hand/GUI
inside pack rendering. Improved-transparency intermediates, custom colortex0
clear/history and padded-RGB scene formats explicitly reject. This feature is
experimental, not full scene/gbuffer compatibility; see HDR_WORLD_SCENE_PROGRESS.md.

The current 26.2 native terrain contract uses legacy projection/depth expressions
in both pack stages. Projected varyings stay conventional; final vertex output
converts to native reverse depth after pack main. Shadow matrices stay conventional
and use a light view aligned with the game's sun/moon pose. See
SHADOW_VIEW_AND_PROJECTION_PROGRESS.md for the corrections and GPU evidence;
the older implementation milestones below retain their historical details.

The 26.2 OpenGL texture-state cache is extended to sixteen slots. Full-screen
activation checks registered tracking capacity and queried fragment/vertex/combined
device limits before allocation. Validated shaders discard unreachable helper
functions and inactive sampler bindings. Distinct shadow-color indices are retained;
the unimplemented second shadow-color attachment is rejected. See
TEXTURE_CAPACITY_PROGRESS.md for the exact scope and hardware verification.

26.2 has client-only hooks into LevelRenderer and ChunkSectionsToRender, plus guarded
ChunkSectionLayer/BufferBuilder hooks for the test-only extended mesh mode. Its JVM-lifetime
flag fixes the chunk stride at startup (28 bytes by default, 48 with the extension).
DefaultVertexFormat.BLOCK and non-chunk pipelines remain unchanged. QuadMeshAttributes fills
the extra normal/tangent/mid-UV bytes after mesh completion, preserving vanilla bytes, sort
indices and the builder's existing ownership. Chunk-layer pipeline format controls allocation
alignment and base-vertex math; shader-off/native world pipelines use that same layout.
No mesh enrichment runs in default mode. The restart-only option and completed mesh GPU checks are
documented in MESH_ATTRIBUTES_PROGRESS.md for the earlier geometry-only layout.
SectionCompiler captures block identity around model/fluid tessellation in a nesting-safe
thread-local integer. BufferBuilder stores it at offset 44; fluid geometry uses its own legacy
block state with a negative sign. Pack IDs are never stored in chunk meshes. An immutable
block.properties rule list is resolved against Forge's active state map into a bounded RGBA8
lookup owned by each compiled world pipeline. Native mc_Entity reads this table in terrain
and shadow vertex stages. Pack activation switches shaders and lookup together; a new client
world refreshes the lookup after connection-time registry remapping. Resize preserves it.
Full pipeline close frees it; world-target resets only free attachments. Named selectors avoid
tag synchronization dependencies; unsupported tags fail explicitly. See MATERIAL_IDS_PROGRESS.md.
WorldRenderHooks orchestrates a separate terrain-shadow submission and MRT terrain draws.
WorldProgramPlan preserves the last active draw-buffer directive for rendering while
conservatively collecting conditional logical targets for allocation. WorldTargetLayout
allocates only used auxiliary indices, enforces a 192 MiB world-texture budget, and
WorldRenderTargets tracks owned world texture count/bytes separately from pack image uploads.
NativeShaderExtensions consumes only the approved ARB_shader_texture_lod directive behaviors
and translates four legacy 2D LOD/gradient calls to GLSL 3.30 core names. Arguments and source
line counts are preserved; unknown directives and unsupported ARB/EXT calls fail explicitly.
This does not add image load/store, texture kinds or sampler bindings. See NATIVE_EXTENSIONS_PROGRESS.md.
NativeShaderDeclarations expands plain grouped uniform/varying/attribute names without
inventing bindings. Both stages validate varying declaration shape, duplicates and matching
types before driver compilation. Arrays and initialized declaration lists remain unsupported.
NativeTerrainProgram transforms a bounded subset of paired pack terrain/shadow sources:
NativeCustomUniforms first lowers supported pack-defined float expressions over frameCounter
or worldTime (positive-literal remainder/division only), injecting dependencies into the
normal native binding pass. Unsupported/ambiguous definitions remain subject to rejection.
This is GPU expression lowering, not the general CPU custom-uniform language; post aliases
are unchanged. See NATIVE_CUSTOM_UNIFORMS_PROGRESS.md for limits and test evidence.

The terrain/shadow transform then binds
real BLOCK position/color/atlas/lightmap inputs, matching simple varyings, ordered MRT outputs,
and atlas alpha discard. Native terrain/shadow fragments use the
pack-owned noise texture. Terrain alone can also sample completed native shadowcolor0 with
a dedicated sampler; producer/readiness gates and frame-start color clearing prevent
intentional feedback and stale empty-scene color. This path still needs GPU validation;
see NATIVE_SHADOW_COLOR_PROGRESS.md. Noise consumers use the
same pack-owned noise image as post-processing when they request noisetex. Native
noise uses a borrowed OculusNoise sampler, with no per-draw upload or duplicate ownership;
see NATIVE_NOISE_GPU_PROGRESS.md for scoped GPU checks and limits. Terrain uses camera projection × chunk
model-view; shadow uses the combined chunk shadow transform. A shared 576-byte std140 block now exposes current camera
projection/view, shadow projection/view, all four inverses, cameraPosition and the main render
target's viewWidth/viewHeight/aspectRatio to both native stages. Each pack owns three fenced
slots (1,728 bytes), updated once per frame, not per chunk. Camera extraction also snapshots
relativeEyePosition into this render state; the padded vector occupies byte 560. See
NATIVE_EYE_PROGRESS.md for semantics and NATIVE_EYE_GPU_PROGRESS.md for scoped hardware checks.
Shadow stages receive the main
viewport dimensions, not shadow-map dimensions. Invalid viewport sizes defer native rendering.
The former padding slot at offset 540 now contains int worldTime, derived from the synchronized
Overworld clock modulo 24000. Offset 544 now contains int worldDay from that same snapshot;
offsets 548/552/556 now contain frameCounter/frameTime/frameTimeCounter from the shared
renderer animation snapshot. GameRenderer HEAD publishes once and RETURN records completed
frame duration; native and post consumers do not advance it. See ANIMATION_CLOCK_PROGRESS.md
for lifecycle semantics and ANIMATION_CLOCK_GPU_PROGRESS.md for scoped runtime checks.
Post-processing worldTime/worldDay
use the same clock source instead of game age. See NATIVE_DAY_PROGRESS.md for the latest CPU
checks and NATIVE_DAY_GPU_PROGRESS.md for the previous 560-byte layout's scoped hardware evidence.
Custom dimension timeline semantics remain unsupported.
See NATIVE_VIEWPORT_PROGRESS.md for the implementation and NATIVE_VIEWPORT_GPU_PROGRESS.md
for the scoped GPU resize/reload and diagnostic negative-control checks.
Named shadow bindings require an actual shadow program. Separate legacy shadow projection,
model-view and inverse-transpose normal transforms use the same captured shadow matrices.
The initial zero-FOV/nonfinite camera frame defers both native world draws and post-processing;
the next valid frame resumes without deactivating the pack or binding stale data. Other runtime
failures still fail closed. Matrix serialization uses bounded ByteBuffer writes, avoiding JOML's
unsafe direct-buffer assumptions on heap-backed buffers. See WORLD_MATRICES_PROGRESS.md.
Unsupported attributes/uniforms/extensions and distinct
terrain-layer programs retain the explicitly logged experimental internal fallback.
BSL/Complementary still use that fallback. Shadow-only packs submit their shadow map while
leaving main terrain on vanilla. This is not a complete world-shader implementation.
World RenderPipeline descriptors are interned in a separate bounded 512-entry cache keyed
by SHA-256 of root, ordered target formats and source fingerprints. Full resource reload clears
both world and post descriptor caches. Native prepared sources count toward the pack source budget.
26.2 ColorBufferFormats reads line-leading metadata from active expanded sources, including
the block-comment tables used by real packs. Active loose format declarations are removed
before GPU compilation; shader expressions referring to them fail explicitly. The completed dimension-wide table applies
to earlier post programs and world auxiliary buffers. ColorBufferPlan assigns typed scratch
slots only for post outputs. Each step distinguishes borrowed world inputs from scratch/main
inputs; logical writes move later readers onto their new scratch producer without modifying
the world attachment. Unlike formats never share a scratch slot. World views are resolved
and size/format-validated for the current frame; no import copies are submitted. A generation
closed by a world-render failure is skipped by the post pass. Budgets count physical storage
bytes once in their owning allocator. See BUFFER_HISTORY_PROGRESS.md for current layouts.
Auxiliary Clear/ClearColor directives select per-frame clearing and initial seed values.
New storage is always initialized; retained outputs are pinned until post processing completes,
then copied to their world attachments. No post stage sees a partially committed history.
World identity, dimension variant, layout/size changes and resource reload invalidate storage;
client logout closes world targets. This does not implement independent ping-pong-side history.
Minecraft lacks GL allocation mappings for its unpacked RGB enums, so these use RGBA
storage at unchanged component precision, clear alpha one and RGB-only writes. Pipeline
identities include logical formats. All mappings are checked before pack activation.
FullscreenColorTransfer performs a draw when a no-final-stage result needs conversion to
the display format. Main world color defaults to borrowed RGBA8; the restart-only
HDR scene flag described above provides declared-format owned color. Main clear/history, mipmaps,
arbitrary flips, partial-output preservation, complete temporal uniforms/reprojection and
full HDR world capture remain unimplemented. Post-only auxiliary history is rejected.

Both packaged-JAR verifiers check that every configured mixin class actually exists.
The 1.21.11 mixin configuration currently has no entries.

## Lifecycle

`OculusForge` creates one `OculusClientService`, registers the resource reload
listener and config screen, contributes `OculusCompositePass` through
`AddFramePassEvent`, and closes GPU resources at client shutdown.

Pack activation follows two thread domains:

1. Reload preparation opens the selected pack, resolves bounded includes, and
   produces immutable adapted source on a preparation executor.
2. Reload application compiles the prepared programs through `GpuDevice` on the
   render thread and atomically installs the resulting pipeline.

Ordinary selection changes use a dedicated daemon preparation worker, not Minecraft's full
asset reload. LatestTaskExecutor bounds work to one running and one queued task; replaced queued
tasks are cancelled. Revision tokens discard stale completions. Disabling immediately invalidates
pending work and closes the active generation without an asset reload.

ShaderReloadState also tracks full-resource epochs. Shader-only results wait while Minecraft
reloads its resources, and only the newest selection is installed after the barrier.
Shader-only preparation/compilation failures retain the still-valid active GPU generation.
After a full resource reload, last-known-good prepared sources are recompiled into fresh
GPU resources because Minecraft may have cleared its program cache. Errors remain visible.
A runtime render failure disables the failed pipeline and restores vanilla.

## Pack trust boundary

```text
shaderpacks
  -> ShaderPackRepository
    -> DirectoryPackFileSystem / ZipPackFileSystem
      -> VirtualPath normalization and bounded strict-UTF-8 reads
        -> ShaderSourceLoader include graph
          -> PackProgramCompiler
            -> Legacy shader adapters
              -> immutable PreparedPackPipeline
```

ZIPs are never extracted. Protections include relative-path enforcement,
canonical directory containment, duplicate archive entry rejection, entry and
archive size limits, compression-ratio limits, include cycle/depth/count limits,
and expanded-source limits.

## GPU pipeline

`PackProgramCompiler` discovers supported stages in deterministic order:

1. `deferred`, `deferred1` through `deferred15`
2. `composite`, `composite1` through `composite15`
3. `final`

Each fragment stage can use its matching `.vsh`; otherwise the compiler supplies
a fixed full-screen vertex shader. Source fingerprints are included in shader
resource identifiers so Minecraft's pipeline cache cannot alias different pack
generations. Full SHA-256 fingerprints also key a bounded PipelineDescriptorCache, which
reuses the exact RenderPipeline object: Minecraft's own cache is identity-keyed. Rebuilding
an equivalent descriptor on every switch would retain extra compiled programs. Before any
GPU allocation, the whole requested generation must fit the 512-descriptor cap. The registry
is cleared only at a completed full resource reload (Minecraft clears its cache) or shutdown.
It must not evict descriptors while Minecraft still owns those keys.

`OculusCompositePass` declares the main target to the Forge frame graph. For an
active dimension variant, it leases same-size scratch color targets using an
immutable `ColorBufferPlan`. Logical attachments retain independent contents;
read/modify/write stages allocate a different physical target before flipping
their logical mapping. Current-frame auxiliary inputs must have an earlier producer.
The planner resolves every producer version and its last read, then assigns physical storage.
An output may reuse a slot only when its prior version's last use is strictly earlier
than this pass; same-pass inputs cannot alias its output. The presented colortex0 version
is pinned through the final copy even when later passes write unrelated attachments.
Simple chains and the round-trip fixture reuse two targets. Main is never an intermediate output:
the presentation target is copied to main once, after the full chain succeeds.
Allocation is capped at 128 MiB of scratch color storage per frame. The current main
depth texture is exposed only as depthtex0/gdepthtex. Stable descriptors are cached by size.

Root, world0, world-1 and world1 are prepared and compiled separately. The matching
dimension variant is selected at extraction, falling back to root. If neither exists,
vanilla rendering is retained for that dimension. No world0 fallback is used elsewhere.
Unsupported renderer entry points, shader properties and buffer directives are rejected
before activation rather than silently skipped.

Programs are represented by:

- `PreparedFinalProgram` / `PreparedPackPipeline`: CPU-side immutable source
- `PackFinalProgram`: compiled pipeline, sampler bindings, and optional uniform
  buffer
- `PackShaderPipeline`: ordered live program generation
- `ShaderPipelineManager`: active generation, failure fallback, and teardown

Minecraft owns cached compiled `RenderPipeline` objects. This port owns and
closes the mapped uniform ring buffers and pack texture/views it allocates. Idempotent ownership
counters aid lifecycle testing; they do not measure Minecraft's cache or total driver memory.

`NoiseTextureData` prepares bounded, immutable deterministic RGB pixels on the preparation
executor only when a used generated-noisetex binding exists. PackTextureBindings resolves the
supported properties subset, rejecting duplicates, conditional properties and unsupported bindings.
PackImageData loads only used PNG paths, deduplicated across stages and variants. It checks PNG
headers/chunk CRCs and dimensions before decoding, rejects APNG/16-bit images, strips ancillary
metadata before ImageIO, and enforces per-image and aggregate decoded-pixel budgets.
Only explicit boolean blur/clamp metadata is accepted.

PackGpuTexture uploads RGBA8 once per compiled generation and owns its view/texture.
Generated noise uses opaque alpha and repeat/linear sampling; PNGs preserve alpha and use
their metadata (default repeat/nearest). Partial-upload failure closes the view/texture;
failed compilation closes earlier programs. No per-frame image allocation/upload is performed.

## Legacy source adaptation

The fragment adapter converts a deliberately bounded subset of legacy GLSL to
GLSL 330:

- `varying` inputs
- `texture2D` and `texture2DLod`
- `gl_FragColor` or `gl_FragData[0]`
- supported indexed color and current main-depth sampler aliases
- a defined set of common scalar, vector, camera, fog, weather, view, and time
  uniforms

The vertex adapter translates legacy full-screen use of `ftransform`,
`gl_Vertex`, and `gl_MultiTexCoord0` to a `gl_VertexID` full-screen triangle.
Unknown uniforms, custom attributes, unavailable color attachments, multiple
fragment outputs, and unsupported built-ins are hard errors.

Minecraft 26.2 stores reversed depth. Recognized depth texture samples are
adapted to the forward-depth value expected by legacy post-processing shaders.
This does not yet solve reversed depth for future terrain or shadow programs.

## Backend policy

The active API is read from `RenderSystem.getDevice().getDeviceInfo()`:

- OpenGL: full-screen pipeline may activate
- Vulkan: shaders automatically disabled with a clear message
- unknown backend: shaders automatically disabled
- OptiFine present: shaders automatically disabled as incompatible

OpenGL shader-pack code is never attempted on Vulkan.

## Allocation and threading rules

- no pack reads, preprocessing, or compilation per frame
- no synchronous shader-pack disk I/O on the render thread (small atomic config saves remain synchronous)
- immutable prepared generations cross the executor boundary
- GPU resources are created, used, and closed on the render thread
- sampler and uniform layouts are compiled per program
- uniform locations are represented by a fixed std140-compatible block
- target descriptors are reused while dimensions are stable

## Required next architecture

Full pack compatibility still needs independently implemented, Forge-compatible
components:

- terrain/chunk gbuffer routing
- entity, block entity, particle, hand, overlay, sky, cloud, weather, and water
  stages
- multiple color/depth targets and pack directives
- shadow world rendering
- automatic material normal/specular maps and the remaining custom-texture formats
- complete profiles/options/preprocessor semantics
- dimension-aware matrices and the remaining Iris/OptiFine uniform model

Those components must preserve the current boundaries: supported Forge APIs
where possible, narrow verified hooks only where required, explicit unsupported
features, transactional GPU ownership, and no incompatible-license code.
