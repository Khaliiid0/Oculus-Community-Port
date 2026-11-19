# Next limited beta — release target

2026-11-19: historical checkpoint notes below are superseded for public build
eligibility by [PUBLIC_BETA_ACCEPTANCE.md](PUBLIC_BETA_ACCEPTANCE.md). The owner
approved preparing 0.4.0-beta.4 and publishing its matching source branch, not
platform uploads. Timed active gameplay remains unmeasured; it is accepted as an
explicit limitation of this smaller beta, not recorded as a passed soak.

Compatibility recovery milestone (2026-11-17): checkpoint 2's user-played session
did not pass the fixed-settings soak: graphics changed and an improved-transparency
frame triggered sticky HDR fallback. Checkpoint 3 now uses a reversible
compatibility pause, retaining the safety guards. Both CPU modes pass 1,139 tests;
packaged pause/resume, reload/replacement while paused, startup pause, manual OFF
and zero-counter shutdown checks pass. See
COMPATIBILITY_PAUSE_QUALIFICATION_2026_11_17.md. Repeat the gameplay soak with
the corrected binary; visual/distribution gates remain unchanged.

Warning attribution follow-up (2026-11-17): production Forge without Oculus
reproduces the missing minecraft:end_of_frame warning at world entry and F3+T,
with the same library hashes and graphics options. This is not an Oculus-specific
failure in the tested setup; the message is not fixed or suppressed. See
POST_EFFECT_BASELINE_2026_11_17.md. The first user-played BSL session was
interrupted by the compatibility failure and is not marked passed. Remaining visual
execution is bounded by LIMITED_BETA_VISUAL_MATRIX.md.

Installed-JAR milestone (2026-11-17): the unchanged checkpoint 2 JAR now passes
selected production-client lifecycle checks from an isolated mods folder, with
no source roots or renderer launch overrides. Activation OFF -> ON -> OFF,
original BSL/Complementary default activation, BSL F3+T reload, failed-selection
last-good recovery, Complementary dimension travel, logout/re-entry and three
zero-counter shutdowns pass. See INSTALLED_JAR_QUALIFICATION_2026_11_17.md.
The missing minecraft:end_of_frame warning needs a no-Oculus baseline; an
uninterrupted gameplay soak, the promised visual/profile matrix and distribution
package remain required. This milestone does not unlock publication.

Activation milestone (2026-11-17): a separate, default-OFF world-renderer setting
now enables extended terrain and HDR together after restart. Three source-client
boots without renderer JVM overrides qualify OFF -> ON -> OFF, with original BSL
terrain/shadow rendering during the ON boot and safe shader disable before the
final restart. Both default and extended/HDR suites pass 1,043 + 82 = 1,125 tests.
Private checkpoint 2 contains the setting; checkpoint 1 is preserved unchanged.
See RENDERER_SETTINGS_QUALIFICATION_2026_11_17.md. This is NOT installed-JAR or
public-release qualification; the remaining release gates below stay open.

Private packaging milestone (2026-11-16): marked binary and developer sources JAR
created successfully, clean-content checks and 1,111 tests pass. All four intent/
publication rejection cases pass. Evidence: test-evidence/2026-11-16-private-beta-packaging.
Installed-JAR runtime and usable activation remain unqualified.

Scope decision: the user approved a smaller beta instead of waiting for complete
Iris/Oculus parity. This does not certify the present development checkpoint or
authorize platform publication.

Target Minecraft 26.3 / Forge / Java 25, client-only OpenGL and classic
transparency. Aim to ship the qualified terrain/shadow/post-processing and static
material improvements with a documented, bounded pack/profile matrix.
No blanket BSL/Complementary compatibility or FPS improvement claim.

Full native entity/hand gbuffer parity, every shader option, animated materials,
all POM/refraction optical behavior and broad hardware/modpack support are outside
this beta's completion target. List actual limitations prominently; unsupported
paths must not be advertised as implemented.

## Required for this beta

1. Usable activation: implemented and source-client restart flow qualified.
   World renderer is OFF by default; enable it in Mods -> Oculus -> Config,
   apply, restart Minecraft, then enable shaders. Legacy JVM flags explicitly
   override the preference. Repeat this flow with the actual installed JAR;
   private audit build flags do NOT bake activation into the binary.
2. Qualified packaged binary: selected production Forge loading, activation,
   shader enable/disable, selection failure recovery, resource reload, dimension
   travel/re-entry and resource teardown checks pass with checkpoint 2. The
   retained post-effect warning reproduces without Oculus; complete and review
   the real gameplay session using the actual JAR before treating this gate as
   complete.
3. A small original-pack visual matrix covering promised profiles and visible
   water/material behavior. Known unsupported entity/hand paths remain explicit.
4. Clean distribution contents, exact source/build inputs, checksums, changelog,
   project description and matching version metadata before publication.

## Private packaging path

The ordinary publication/default-launch gate remains closed. A separate explicit
local build intent allows a marked private JAR for installed-JAR testing:

    .\gradlew.bat -p versions/26.3 preparePrivateBetaCandidate -PoculusPrivateCandidate=true -PoculusExtendedTerrain=true -PoculusHdrWorldScene=true --max-workers=2 --warning-mode=fail

Requires Java 25, full regression/API tests and fault-harness isolation.
Outputs are isolated in build-private-candidate, with a private-audit version
suffix in filenames, mod metadata and manifests; PRIVATE-UNQUALIFIED is embedded
in the binary. Source-only/fault runtime roots cannot be combined with this mode.
These are local audit files, NOT files to upload. The developer sources JAR is
not by itself the complete public release/source publication package.

verifyPortReadiness still rejects even when private intent is enabled. No private
flag removes that publication decision. Keep frozen 26.2 artifacts and previous
qualification evidence untouched.
