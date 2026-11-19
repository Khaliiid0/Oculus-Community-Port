# Native layer routing probe

26.2 extended-terrain diagnostic only, not a normal appearance shader.
Solid outputs red, cutout green, water blue plus a yellow marker in colortex4.
Final turns correctly matched blue+yellow water pixels cyan; a mismatched marker
turns magenta. Generic terrain is magenta, making accidental generic selection visible.

Requires separately visible solid, cutout and translucent terrain in a copied world.
Check actual geometry regions and logs, not sky, HUD, entities or held items. Water
auxiliary markers at object edges may be affected by filtering; interior pixels are
the useful checks. No alpha-texture/material quality or performance claim.

CPU-prepared and regression tested. On 2026-10-13 the packaged 26.2 build passed
the AMD GPU marker test: stone red, leaves green, stained glass cyan; a water-stage
negative control became magenta and restored to cyan after reload. This verifies
translucent terrain routing, not real water materials or arbitrary per-layer bindings.
See TERRAIN_LAYER_GPU_PROGRESS.md for evidence and remaining limitations.
