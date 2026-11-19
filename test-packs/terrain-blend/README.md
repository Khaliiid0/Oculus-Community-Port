# Native per-buffer terrain blend control

Developer fixture, not a distributable shader pack or artistic effect.
Use a copied world with translucent stained-glass geometry visible above a stone
floor. The terrain program makes that floor pure red; scene blending must yield
(0.5, 0, 0.5, 1), not unblended blue. Water-stage
output order is intentionally [colortex4, colortex0, colortex8], not sorted.

colortex4 and colortex8 disable blending, preserving RGB and alpha 0.25.
Scene colortex0 retains separate-alpha translucent blending. Different enabled
per-buffer factor tuples remain unsupported by the stock Forge descriptor and
reject explicitly during CPU preparation. The final stage
marks covered translucent geometry green only when both auxiliary raw samples
match. Wrong blending/routing is magenta; unrelated scene pixels remain normal.

Negative control: in the isolated profile ONLY, replace the colortex4 `off`
setting with SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA, then F3+T.
Covered geometry must turn magenta. Restore `off`, reload, and require green.
This checks attachment blending, not physical water/material visual parity.
