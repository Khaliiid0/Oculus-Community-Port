# Block layer routing diagnostic

Developer fixture, not a visual preset. Same shader programs as native-terrain-layers:
solid=red, cutout=green, translucent writes blue scene plus yellow colortex4;
post turns matching blue/yellow into cyan, or magenta for an auxiliary mismatch.
Only block.properties differs: oak_leaves model quads must enter TRANSLUCENT.
No shader requests material IDs, so layer directives must load independently.

Use a copied test world with the existing floating oak leaf, and cutoutLeaves=false
in disposable options to exercise forceOpaque as well as ordinary quad output.
With this pack the leaf must be cyan. Live-switch to native-terrain-layers: it
must rebuild to red under this option (green with cutoutLeaves=true). Switch back:
cyan. This checks model mesh routing/MRT, not full transparent surface shading.
Fluids, vanilla face visibility and occlusion metadata retain vanilla behavior.
Disable shaders, then re-enable, reload and resize; retain screenshots/logs.
