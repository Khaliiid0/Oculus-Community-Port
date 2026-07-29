# Test Report

Date: 2026-07-28

Release: `0.2.0-alpha.1`

Status: functional full-screen renderer alpha; production acceptance not met

## Environment

| Item | Observed |
|---|---|
| OS | Windows 11 amd64 10.0 |
| Java | Eclipse Temurin OpenJDK 25.0.4+7-LTS |
| Minecraft | 26.2 |
| Forge | 65.1.0 |
| Forge MCP build | 20260616.103818 |
| Backend | OpenGL 4.6 core |
| GPU | Intel Iris (TM) Graphics |
| Driver string | 26.5.2.260413 |
| LWJGL | 3.4.1+2 |

## Automated verification

Commands:

```powershell
.\gradlew.bat clean test build packageRelease --no-daemon
```

Result: **passed** - 35 tests in 10 suites, 0 failures, 0 errors, 0
skipped.

The clean command completed compilation, resource expansion, sources packaging,
the full test suite, JAR verification, and checksum generation. A cached
follow-up invocation returned `BUILD SUCCESSFUL` with all verification tasks
successful.

The suite covers:

- semantic-version parsing and precedence
- configuration round trip and unsafe-value fallback
- backend detection and policy
- virtual-path traversal rejection
- directory/ZIP discovery and wrapper directories
- malformed ZIP, traversal, file-size, compression-ratio, and include limits
- missing includes, include cycles, and source maps
- shader option parsing and bounded object macro expansion
- fragment adaptation, sampler classification, depth conversion, output and
  uniform rejection
- full-screen vertex adaptation and unsupported built-in rejection
- required JAR resources, duplicate classes, and production artifact naming

## Live Forge client verification

A synthetic shader pack named `Oculus-FrameGraph-Test` was used. It is local
test input and is not bundled in the mod.

### Startup and world

- Forge 65.1.0 launched Minecraft 26.2 on Java 25.
- Oculus identified OpenGL and admitted the pipeline.
- The title flow completed and an integrated `New World` server started.
- The development player logged into the world.
- Multiple client startups completed.
- A clean Minecraft shutdown was observed on shader-enabled runs.

### Shader execution

- A single `final.fsh` compiled, activated, and visibly transformed the world.
- A legacy fixed-function `final.vsh` compiled with the fragment stage.
- A two-stage `composite` + `final` pack compiled and executed in order.
- The log recorded execution at 1920x1080:
  `Executed shader pack 'Oculus-FrameGraph-Test' (2 full-screen stage(s))`.
- A separate run rendered at 876x536, exercising size-specific target creation.
- No Oculus exception, frame-graph failure, OpenGL error, fatal mixin error,
  missing class, or invalid access transformer appeared.

### Shaders-disabled fallback

With `shaders.enabled=false` and the same selected pack:

- the integrated server started;
- the player logged into the world;
- no pack activation or shader execution message appeared;
- vanilla rendering remained the active path.

That last run's GUI process did not expose a closable main-window handle to the
automation harness and was terminated after the evidence was collected. This
does not count as the clean-shutdown test; clean shutdown was established in an
earlier enabled run.

### Unrelated log messages

- Mojang Realms authentication failed for the offline development account.
- Forge userdev emitted `union:` asset-schema warnings.
- vanilla world generation emitted unsafe terrain-read diagnostics.

No stack frame in those messages originated from this mod.

## Acceptance matrix

| Gate | Result |
|---|---|
| Clean wrapper build | Pass |
| Unit tests | Pass - 35/35 |
| Binary and sources JARs | Pass |
| Metadata/resources/duplicates | Pass |
| Forge/OpenGL startup | Pass |
| World loads | Pass |
| One shader renders | Pass |
| Ordered multi-pass rendering | Pass |
| Shaders-disabled vanilla fallback | Pass |
| Legacy full-screen vertex compatibility | Pass |
| Window-size target recreation | Partial pass |
| Clean shutdown / second startup | Pass |
| Live enable/disable through UI | Unverified |
| Fullscreen F11 toggle in one session | Unverified |
| Resource reload and pack switching matrix | Unverified |
| Dimension travel | Unverified |
| Terrain/entity/hand/sky pack routing | Fail - not implemented |
| Shadow maps | Fail - not implemented |
| Representative real shader-pack matrix | Unverified |
| Mod/resource-pack compatibility matrix | Unverified |
| Vulkan safe-fallback runtime | Unverified |
| Performance and leak profile | Unverified |
| Standalone launcher mods-folder run | Unverified |

## Conclusion

The release advances from a metadata-only foundation to an actual, tested
Forge frame-graph shader renderer for compatible full-screen stages. It still
fails the original production acceptance criteria because world gbuffer,
shadow, full shader-pack semantics, compatibility-mod, and representative-pack
verification remain incomplete.

## Release artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `oculus-forge-26.2-0.2.0-alpha.1.jar` | 211,258 | `8076ddbe5de834c1187c7d88d078332e26e7fe5e20e104bafa944c42a1aada52` |
| `oculus-forge-26.2-0.2.0-alpha.1-sources.jar` | 142,268 | `1e99cea4dfdd519da7d820e606dc5edfe93df321ca0c48aca51899094133b78a` |

The binary contains 50 production classes plus Forge metadata, `pack.mcmeta`,
the icon and translation, LGPL license, license notes, and notice. No test
classes, duplicate classes, mixin configuration, refmap, or access transformer
is present.
