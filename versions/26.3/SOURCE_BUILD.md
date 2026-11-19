# Minecraft 26.3 — 0.4.0-beta.4 source build

This module targets **Minecraft 26.3 / Forge 66.1.2 / Java 25**. It shares code
and resources with `../../src`; the module alone, or its generated sources JAR,
is not a self-contained build checkout. Keep the archived directory layout.
The root README/dependency notes describe older targets and are not this module's
build instructions.

## Build inputs

| Input | Pinned version |
| --- | --- |
| Gradle wrapper | 9.6.0, in the root `gradle/wrapper` directory |
| ForgeGradle | 7.0.31 |
| Shadow | 9.4.2 |
| Foojay toolchain resolver | 1.0.0 |
| EventBus validator | 7.0.6 |
| Mixin processor | 0.8.7 |
| JCPP | 1.4.14; bundled/relocated, Apache-2.0 notice in `third_party` |
| JUnit | 6.1.2; tests only |

These versions come from the checked-in build files, not recommendations for
upgrading the tested runtime. Java 25 is required; this is not a Java 21/22 build.
Dependencies are resolved by Gradle from the repositories in `build.gradle`.
Minecraft/Forge libraries, third-party shader packs, private launch arguments,
user saves, account data, caches and binary mod artifacts are not source inputs
to include in the source archive.

From the **archive root**, with an installed Java 25 JDK:

```powershell
.\gradlew.bat -p versions/26.3 test portApiAudit verifyFaultAuditIsolation --max-workers=2 --warning-mode=fail --console=plain
.\gradlew.bat -p versions/26.3 build sourcesJar --max-workers=2 --warning-mode=fail --console=plain
.\gradlew.bat -p versions/26.3 test portApiAudit -PoculusExtendedTerrain=true -PoculusHdrWorldScene=true --rerun-tasks --max-workers=2 --warning-mode=fail --console=plain
```

The ordinary build produces `versions/26.3/build/libs/oculus-forge-26.3-0.4.0-beta.4.jar`
and its `-sources.jar`. The `-slim.jar` is an intermediate without the bundled
preprocessor: do not upload it. Test properties select test modes; they do not
bake the renderer preference into the mod. World rendering stays OFF by default.

`verifyPortReadiness` checks the reviewed, exact-version limited-beta acceptance
and CPU/API/fault-isolation prerequisites. It rejects other versions/targets and
source/fault runtime roots. This is not a live public-source availability check
or platform upload authorization. See `PUBLIC_BETA_ACCEPTANCE.md` for scope.

The historical private audit task remains separate and marked PRIVATE-UNQUALIFIED
in `build-private-candidate`. Never rename its outputs into public beta files.

The renderer Java is unchanged from qualified private checkpoint 4. Public
version metadata, notices and build acceptance are newly packaged and require
their own artifact checks and clean-source reproduction. Never apply private
checksums to the public files. Dependencies may need network access on first
build; Gradle `--offline` is not proof that plugins perform no network checks.

## Archive policy and notices

`tools/Prepare-CorrespondingSource.ps1` (in this module) uses an explicit source/
build-input allowlist, includes untracked source files, rejects links/traversal,
and verifies every ZIP entry against a SHA-256 inventory. It refuses to overwrite
existing archives. Test-only authored GLSL fixtures belong in this source tree;
they must remain excluded from the runtime mod JAR.

Root `LICENSE` is LGPLv3; `licenses/GPL-3.0.txt` supplies the accompanying full GPLv3
text from GNU. The project remains LGPL-3.0-only, not relicensed to GPL-only.
See [GNU's inclusion guidance](https://www.gnu.org/licenses/gpl-howto.html).
Preserve both root provenance notices and the module's `NOTICE`/`LICENSE_NOTES.md`,
plus the JCPP Apache notice. Binary/sources packaging includes
both full GNU texts and the module-specific notices. `verifyDistributionNotices`
checks their exact bytes in both archives. This is not public-source availability
or general legal approval. Frozen private checkpoint artifacts stay unchanged.

Publish the exact qualified source revision/archive at a public, correctly
labeled project Source link before resubmitting to a distribution platform.
The version-specific source target is:
https://github.com/Khaliiid0/Oculus-Community-Port/tree/release/26.3-0.4.0-beta.4
A URL printed here alone is not verification that the branch is publicly present.
