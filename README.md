# Oculus Forge 26.2 Community Port

> Unofficial functional alpha for Minecraft 26.2 / Forge 65.1.0

This repository is an independent, LGPL-3.0-only community port. It is not an
official Oculus, Iris, Forge, Mojang, Microsoft, Sodium, or Embeddium release.

Version `0.2.0-alpha.1` contains a working OpenGL frame-graph shader path. It can
load a shader-pack directory or ZIP, compile compatible legacy `deferred*`,
`composite*`, and `final` programs, execute them in order over the rendered
world, update common uniforms, and return safely to vanilla rendering when
disabled or when compilation fails.

This is still an alpha, not the complete Oculus compatibility renderer requested
for production. Packs that require terrain/entity gbuffer programs, shadow
rendering, additional color attachments, normal/specular textures, custom
textures, or the full OptiFine/Iris directive language will not work yet. See
[KNOWN_ISSUES.md](KNOWN_ISSUES.md) before testing.

## Target

- Minecraft Java Edition 26.2
- Minecraft Forge 65.1.0 (`net.minecraftforge:forge:26.2-65.1.0`)
- Java 25
- Gradle 9.5.0 and ForgeGradle 7.0.31
- OpenGL only for shader-pack rendering
- Vulkan detected and rejected with vanilla fallback
- OptiFine detected and rejected

## Implemented

- live Forge 26.2 `AddFramePassEvent` integration without mixins or access
  transformers
- ordered `deferred*`, `composite*`, and `final` full-screen stages
- compatible legacy fragment and fixed-function full-screen vertex adaptation
- main color and depth sampling, including forward-depth emulation for legacy
  depth reads on Minecraft's reversed-depth target
- common time, frame, camera, fog, sky, weather, view, and world uniform updates
- asynchronous pack preparation and render-thread GPU compilation
- live enable/disable and pack switching through resource reload
- cached pipeline descriptors and mapped uniform ring buffers
- safe fallback when source adaptation, compilation, or pass execution fails
- shader-pack directory and non-extracting ZIP discovery
- bounded includes, strict UTF-8, cycle/path traversal/decompression defenses
- Forge-native configuration screen and atomic configuration persistence
- production and sources JARs, checksums, duplicate-class checks, and required
  resource verification

## Performance and compatibility policy

Shaders-disabled rendering leaves vanilla world rendering intact and does not
compile or execute shader programs. Enabled rendering uses Minecraft's GPU
abstraction and Forge frame graph, keeps source and archive work off the render
thread, caches stable descriptors, and avoids per-frame pack I/O or compilation.

Rubidium is not used: it is obsolete for this target. No authoritative
Forge-compatible Embeddium/Sodium release for Minecraft 26.2 was found. This
alpha therefore integrates with Forge/vanilla locally rather than fabricating a
dependency or copying incompatible Sodium code. Future renderer integrations
must be optional, API-guarded, and license-compatible.

The compatibility goal remains unmodified ShadersMod/OptiFine packs, but this
release only implements the full-screen portion of that model. Unsupported
samplers, uniforms, outputs, and programs produce an explicit diagnostic and
vanilla fallback; they are not silently replaced with fake values.

## Build

Use a Java 25 JDK:

```powershell
.\gradlew.bat clean build
.\gradlew.bat packageRelease
```

Linux:

```bash
./gradlew clean build
./gradlew packageRelease
```

Artifacts:

- `build/libs/oculus-forge-26.2-0.2.0-alpha.1.jar`
- `build/libs/oculus-forge-26.2-0.2.0-alpha.1-sources.jar`
- matching `.sha256` files

The development branch is `port/forge-26.2`.

## Test

Place the binary JAR in a Forge 65.1.0 Minecraft 26.2 `mods` folder. Put shader
packs in the instance's `shaderpacks` directory and select them through
Mods -> Oculus -> Config.

The included runtime evidence covers OpenGL startup, title/world loading,
single- and two-stage shader execution, legacy `final.vsh`, shaders-disabled
fallback, window-size target recreation, repeated startup, and clean shutdown.
It does not cover a representative real-world shader-pack matrix or every
acceptance scenario. Exact evidence is in [TEST_REPORT.md](TEST_REPORT.md), with
remaining procedures in [MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md).

## License and attribution

The port is licensed under LGPL-3.0-only; see [LICENSE](LICENSE),
[LICENSE_NOTES.md](LICENSE_NOTES.md), and [NOTICE](NOTICE). Corresponding source
is this source tree and the generated sources JAR.
