# Local-only 26.3 water/dimension scene

Authored developer datapack, NOT a mod or shader-pack distribution. Install only
in `run-runtime-audit/saves/Oculus263RuntimeAudit/datapacks/oculus-water-audit/`
while that disposable save is closed. Never install in a player's normal save.
Pack version 121.0 is verified against the pinned 26.3 sources/metadata codec.
There are NO load/tick tags: every action requires an explicit `/function` call.

- `/function oculus_audit:build`: constructs a 6-block-deep raised pool with a
  contrasting floor and an observation platform, x20..32/z2..18/y-61..-52 only.
  It does not overlap the existing shallow-pool/stone/leaf comparison scene.
- `above`, `under`, `under_up`: reproducible cameras for surface/underwater views.
  Above-water camera stands on the glass platform, underwater camera on the floor.
- `restore`: returns to the original comparison position and resets day/weather.
- `nether` / `end`: enter the selected dimension. After chunks finish loading,
  call `pad` to prepare a 7x5x7 observation room at its origin, with a lit floor.
  `pad` explicitly refuses to modify the Overworld. Do not use in normal worlds.

Compare original BSL/Complementary packs and shader-off at identical cameras.
These views can expose defects; looking plausible is not proof of correct
refraction, HDR/depth values, all pack profiles, or performance/stability.
