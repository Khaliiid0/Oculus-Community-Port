# Split opaque terrain probe

26.2 extended-terrain diagnostic only, not a normal appearance shader.
Solid outputs red plus magenta in colortex5, cutout writes yellow to colortex6
first and green to colortex0 second; water blue plus a yellow marker in colortex4.
Final turns correctly matched blue+yellow water pixels cyan; a mismatched marker
turns magenta. Generic terrain is magenta, making accidental generic selection visible.

Requires separately visible solid, cutout and translucent terrain in a copied world.
Check actual geometry regions and logs, not sky, HUD, entities or held items. Water
auxiliary markers at object edges may be affected by filtering; interior pixels are
the useful checks. No alpha-texture/material quality or performance claim.

Final also verifies solid/cutout auxiliary markers. Use geometry interiors only.
See TERRAIN_OPAQUE_SPLIT_PROGRESS.md for test results and limitations.
