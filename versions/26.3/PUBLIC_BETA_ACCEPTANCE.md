# 0.4.0-beta.4 — limited-beta build acceptance

Decision date: 2026-11-19. The owner approved preparing this smaller public beta
and publishing its matching source on a separate GitHub branch. This does not
authorize uploading to CurseForge or Modrinth.

This is explicit, version-scoped acceptance of the **limited** renderer, not a
private flag bypassing an unfinished-port hold. The ordinary Gradle build checks
`release-acceptance.json`, the fixed target and regression/API/fault-isolation
prerequisites. Other versions require another review. Source-only and fault
runtime roots cannot produce public binaries.

## Reviewed local qualification

- Checkpoint 4: 1,057 regression + 82 API tests per default and extended/HDR
  mode; zero failures, errors or skips in fresh runs and extracted-source builds.
- Two identical complete 1,119-file source exports reproduced the checkpoint 4
  binary and sources JAR byte-for-byte in a new directory.
- Both JARs contain exact full LGPLv3, accompanying GPLv3 and JCPP license texts
  with module-specific notices. Checkpoint 4 changed packaging, not Java.
- Production installed-JAR checks cover bounded activation/restart, selection/
  recovery, reload, resize, dimension/lifecycle, compatibility pause and resource
  cleanup. The exact checkpoint 4 displayed Complementary and exited normally
  with all eight tracked counters zero.
- Original BSL v10.1.1 / Complementary Reimagined r5.7.1 received bounded terrain,
  shadow, water, static-material and lighting/weather observations. Near/return
  fog controls do not support shipping a cloud/fog bypass.
- The owner reported no problems in corrected BSL gameplay and no settings
  changes. Active minutes are unknown: **a timed active soak has not passed**.

Frozen local records have 682 notice/source/smoke evidence hashes and 55 fog
control hashes. Worlds, launch arguments, logs, account data, third-party shader
packs and evidence screenshots are intentionally not public source inputs. This
synopsis is not a claim that the source export contains those runtime records.

## Accepted scope and remaining release checks

Minecraft 26.3, Forge 66.1.2, Java 25, OpenGL client, classic transparency.
World rendering stays OFF by default; enable in Oculus Config and restart.
Improved Transparency pauses custom rendering and classic mode resumes it.
Native entity/hand gbuffer parity, animated materials, arbitrary POM/refraction
profiles, broad modpacks/hardware and full Iris compatibility are not qualified.
No measured FPS improvement, stable-release or normal-launcher claim is made.

Public metadata/notices must be rebuilt, never obtained by renaming a private JAR.
Final-public artifact checks, source reproduction, public source-link verification
and an installed-public-JAR smoke remain release-package qualification steps.
Their results/checksums belong in the accompanying final qualification report;
this source-controlled acceptance does not invent their outcomes in advance.
Distribution is conditional on those checks. Platform upload remains separate.
