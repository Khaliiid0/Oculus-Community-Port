# Minecraft 26.3 — 0.4.0-beta.4 limited beta

Current decision (2026-11-19): [version-scoped limited-beta build acceptance](PUBLIC_BETA_ACCEPTANCE.md).
Use [SOURCE_BUILD.md](SOURCE_BUILD.md) for complete source and public build
commands. Forge 66.1.2 / Java 25 / OpenGL / classic transparency; world renderer
OFF by default, enable in Config and restart. BSL v10.1.1 and Complementary
Reimagined r5.7.1 have bounded checks, not universal options/full Iris parity.
Native entity/hand and animated/POM/refraction paths remain limited.

Public artifact/source/link/installed-JAR verification remains separate final
packaging work. Private checkpoints are not upload files. The development
milestones below are historical; their holds/test counts apply to those earlier
checkpoints, not the version-scoped acceptance above.

Target: Minecraft 26.3, Forge 66.1.2, Java 25. This is an isolated 0.4.0 beta
development target, not a relabelled 26.2 release. Existing 26.2 candidates stay
untouched. This target shares portable source/tests with the repository root;
files under this target's `src/` replace matching shared files explicitly.

Current release target: a [limited beta](LIMITED_BETA_PLAN.md), not complete Iris
parity. Explicitly marked private JARs now exist for installed-JAR qualification
in build-private-candidate. They are NOT upload files; ordinary publication remains
held. Native/HDR user activation and packaged runtime testing still need work.

Latest qualification: [post-only and world-epoch wetness](WETNESS_LIFECYCLE_QUALIFICATION_2026_11_16.md).
Post-only wetting/drying, fault detection, reload and logout/re-entry pass. The
native independent recurrence passes Nether/End/Overworld reset checks; a
first-frame fault remains latched across 53 seconds. Both final test modes pass
**1,029 + 82 = 1,111**. Sixteen unchanged screenshots and evidence verification
are retained; normal exit, zero tracked counters and restored settings pass.
No production timing change, full renderer/optical parity or release is implied.

Previous correction: [shared native/post wetness](WETNESS_QUALIFICATION_2026_11_16.md).
One once-per-frame variant-owned filter supplies terrain, shadow and post stages,
with independent half-life settings and preserved BSL helper-parameter scope.
Full BSL advanced LabPBR now compiles and renders with rain reflections ON on both
native terrain backends, including mapped-resource reload. Independent wetting/
drying and intentional fault controls pass; default and extended/HDR suites each
pass **1,020 + 82 = 1,102**. Evidence verification, normal exit and zero tracked
counters pass. Full optical/renderer/soak/performance qualification is still open:
**no public 26.3 JAR is ready**.

Previous correction: [explicit material options and multiline native parameters](OPTION_PROFILE_QUALIFICATION_2026_11_12.md).
Bounded sidecar settings now apply to untouched ZIPs, with consistent shader/custom
uniform/material branches. A real multiline `inout` masking bug is fixed. Both
terrain backends render scoped Complementary/BSL LabPBR profiles with mapped
normal/specular inputs; option/reload failure recovery passes. At that checkpoint
full BSL rain reflections still required native `wetness`; the tested OFF setting
was explicit. Those default and extended/HDR suites each passed **994 + 82 = 1,076**. No FPS gain,
full profile/optical parity or release is claimed. See [option limits](SHADER_OPTIONS.md).

Previous correction: [resource-pack material-format context](MATERIAL_FORMAT_QUALIFICATION_2026_11_11.md).
Generic/versioned LabPBR macros, native/post code, custom uniforms and material
maps now agree with atlas loading and the incoming resource reload. Mismatched
prepared sources cannot activate/restore against changed formats. All three
contexts pass both actual terrain backends, negative/fallback/recovery controls
and unchanged original-pack ShaderC checks. Default and extended/HDR suites each
pass **975 + 82 = 1,057**; full PBR profiles and publication remain unqualified.

Previous addition: [static normal-map material inputs](NORMAL_ATLAS_QUALIFICATION_2026_11_10.md).
Native terrain/shadow normal atlases now preserve RGBA, linear data mips, original
UV placement and flat defaults, with generation-safe cleanup. Both GPU backends,
deliberate errors and resource-reload rejection/recovery are verified. Current
default and extended/HDR suites at that checkpoint passed 958 + 82 = 1,040 tests. Full PBR/profile
correctness and release qualification are still open; publication remains held.

Previous correction: [GPU center-depth qualification](CENTER_DEPTH_QUALIFICATION_2026_11_10.md).
Rendered center depth now replaces the short-range interaction-ray estimate;
five controls cover both terrain backends and independent GPU temporal checks.
Current default and extended/HDR suites each pass 944 + 82 = 1,026 tests. This
does not lift the publication hold or certify complete BSL/Complementary parity.

Run CPU and independent prerequisite tests from the repository root using Java 25:

```powershell
.\gradlew.bat -p versions/26.3 test portApiAudit --max-workers=2 --warning-mode=fail
```

Java compilation and CPU tests now pass. The full port build is still blocked
by qualification; this is not an upload workflow:

```powershell
.\gradlew.bat -p versions/26.3 build --max-workers=2 --warning-mode=fail
```

The renderer's Java sources compile. Real ShaderC now accepts prepared fixture
pairs for fullscreen, terrain/shadow, End-sky and presentation paths. Separate
and indirect terrain layouts now pass real SPIR-V reflection/buffer-writer checks.
An isolated source client now launches and renders the basic native terrain/shadow
fixture on AMD integrated OpenGL, including resource reload, resize and shader
disable. Basic extended-input and HDR main-scene diagnostic controls now also
pass on actual indirect and separate draws; the separate path survives reload
and HDR resize. Negative controls fail as expected and recover on both backends.
A filtered-edge false-failure path in the HDR diagnostic was corrected by
sampling texel centers; renderer filtering was not changed. These are limited flat-world checks,
not real-pack or installed-JAR qualification. Packaging and
default client launch still depend on the failing `verifyPortReadiness` gate.
The explicit source-only runtime-audit path is documented in `RUNTIME_AUDIT.md`;
it never unlocks JAR tasks. No production 26.3 JAR is supplied.

Real BSL v10.1.1 and Complementary Reimagined r5.7.1 now pass the real-ShaderC
audit and activate native terrain/shadows/post stages in the source client.
Comma-separated stage interfaces and pack alpha-test policy are implemented.
The deferred/translucent schedule is now split correctly; both original packs'
shallow-water patches render on separate and indirect terrain draws. BSL also
survives reload and fullscreen/windowed resizing. These limited regression
checks do not qualify full compatibility or all renderer features. See
`SHADERC_AUDIT.md` and `RUNTIME_AUDIT.md` for exact scope and failed evidence.

The next follow-up also corrects native/post `far` consistency: both now use
captured effective render-distance blocks, not the camera's clipping distance.
Deep-water and underwater views, authored deferred handoff controls and initial
dimension transitions are documented in `WATER_QUALIFICATION_2026_11_05.md`.
These are development qualifications, not a distributable release.

Deferred exception paths now have whole-batch handoff validation and bounded
scratch ownership. A separate source-only harness passed four bounded failure
points on each real terrain backend, including HDR lease/cleanup and healthy
reload. Full default and extended/HDR suites pass 1,002 tests. See
`DEFERRED_FAILURE_QUALIFICATION_2026_11_05.md` for evidence and limits; this does
not certify context-loss recovery or atomic rollback of issued GPU copies.

The next correction preserves `depthtex2` as opaque pre-hand depth instead of
aliasing the live depth after water/glass. Numeric native/post reconstruction
controls, intentional negatives and recovery pass on both terrain backends.
Current full suites pass **1,011 tests**. Original BSL/Complementary retain the
documented deep-water/underwater views. Details and scope:
`DEPTH_SNAPSHOT_QUALIFICATION_2026_11_10.md`. Native hand support and full optical
refraction parity are not implied.

Do not publish merely because compilation or basic diagnostic fixtures pass.
Broader scenes, precise extended-input accuracy, repeated lifecycle transitions
and final-JAR runtime still need qualification, as do the existing incomplete
renderer paths. Full BSL and
Complementary compatibility and improved FPS are not claimed.

See `PORT_26_3_PROGRESS.md` at the repository root for verified results and
remaining work. No 26.2 screenshots are evidence of a 26.3 runtime test.
