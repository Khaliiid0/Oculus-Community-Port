# Native far diagnostic

Test in an isolated copied world: terrain should be red at effective render distance
8 chunks (128 blocks), green at 16 (256), blue for other positive distances. Magenta
means invalid/nonpositive or mismatched vertex/fragment values. Verify actual terrain
interiors, not sky/HUD/held items. Change render distance through Minecraft settings,
then restore it and test reload. Shader deliberately does not show normal materials.
Packaged AMD run passed 8 -> 16 -> reload -> 8 on 2026-10-13;
see NATIVE_FAR_GPU_PROGRESS.md for evidence and limitations.
