# Changelog

## 0.2.0-alpha.1 - 2026-07-28

Functional full-screen shader milestone; not a complete Oculus port.

### Added

- Forge 65 `AddFramePassEvent` world-frame integration
- ordered `deferred*`, `composite*`, and `final` full-screen programs
- render-thread program compilation through Minecraft's GPU device
- legacy fragment and fixed-function full-screen vertex adapters
- main color/depth samplers and reversed-depth sample conversion
- common frame, time, world, weather, view, camera, fog, and sky uniforms
- multi-pass temporary target management and window-size-aware descriptors
- live config-driven enable, disable, selection, and resource reload
- recoverable source, compile, and runtime failure handling
- mapped uniform buffer cleanup at replacement and shutdown
- adapter/compiler tests and live Forge world-rendering evidence
- original shader-prism project icon replacing the upstream Oculus logo

### Changed

- OpenGL backend policy now admits the implemented full-screen pipeline
- shader selection UI reports active and failed state
- documentation now distinguishes working full-screen support from missing
  gbuffer/shadow/full-pack support

### Verified

- OpenGL Forge startup and world load
- one-stage and two-stage shader execution
- legacy `final.vsh` compilation
- shaders-disabled vanilla fallback
- 876x536 and 1920x1080 targets
- clean shutdown and repeated startup

## 0.1.0-alpha.1 - 2026-07-28

Secure build and shader-pack input foundation.

### Added

- Java 25, Gradle 9.5.0, ForgeGradle 7.0.31, and Forge 65.1.0 build
- modern Forge metadata, lifecycle, resource reload, and config screen
- OpenGL/Vulkan/unknown detection and OptiFine rejection
- bounded directory/ZIP discovery, include resolution, and source diagnostics
- traversal, archive, decompression, size, cycle, and malformed UTF-8 defenses
- shader option parser, object macro helper, SemVer, and atomic config
- automated tests, artifact checks, sources JARs, and SHA-256 generation

### Removed

- obsolete Loom, Java 8, Forge 1.16.5, Rubidium, CurseMaven IDs, mixins, SRG
  access transformer, bundled JARs, and AGPL shader-transformer dependency
