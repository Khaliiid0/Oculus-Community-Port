# Opt-in live lifecycle test

Use only an isolated ForgeGradle development client and a disposable test world. This
probe changes the selected shader pack, opens the configuration screen when finished,
and leaves the last normal-view fixture active for visual inspection. It does not exit
Minecraft automatically. Never launch it against a player's normal instance.

1. Use the target JDK (root: Java 25; versions/1.21.11: Java 21).
2. Copy the root test-packs/noise-preview, buffer-roundtrip, custom-texture-roundtrip,
   unsupported-probe and compile-error-probe folders into that target's run/shaderpacks.
3. Create an isolated world named BufferRoutingTest (or adjust the quick-play argument).
4. Run:

   `gradlew.bat runClient -PoculusLifecycleTest=true "-PoculusLifecycleSwitches=60" "--args=--quickPlaySingleplayer BufferRoutingTest" --warning-mode=fail --max-workers=2`

   The switch count must be a multiple of four from 60 through 2000. Total checks are
   the selected switch count plus eight failure/reload/disable checks.
5. Inspect run/logs/latest.log for OCULUS_LIFECYCLE_PASS and confirm there
   is no OCULUS_LIFECYCLE_FAIL. Expected compile-error-probe driver errors are deliberate.
6. Inspect both the config UI and unobstructed world: no diagnostic stripe/split screen.
7. Close the client normally. Check the shutdown ownership counts are all zero. Set
   run/config/oculus.properties to shaders.enabled=false and an empty shaderpack.
8. Rebuild normally without the property before packaging.

The probe is a separate source directory, enabled only by this explicit property.
shadowJar and sourcesJar refuse packaging while it is enabled; production verification
also rejects dev/oculusforge/testing classes and fixture paths.

## Checks

- A configurable number of ordinary switches: generated noise, PNG+view uniforms, disabled,
  and normal color round-trip.
- Unsupported preparation failure and actual GPU compilation failure preserve the previous pack.
  The GPU-negative pack compiles a uniform-using deferred stage before its final-stage error,
  exercising cleanup of partially allocated programs.
- Successful replacement, full resource reload, full reload overlapping a newer selection,
  12 rapid selections (newest wins), disable, and re-enable.
- Several rendered frames per completed action; active/error state, owned image count/bytes,
  uniform-ring wrapper count and bounded descriptor identity reuse.

These counters cover resources owned by the mod, not all Minecraft/driver allocations.
This short test is neither a benchmark nor a long-duration leak qualification. Userdev
launches do not prove installed production-JAR compatibility.
