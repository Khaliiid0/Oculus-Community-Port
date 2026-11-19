# Authored depth snapshot/reconstruction oracle

Local developer fixture, not a public shader pack or real-pack derivative.
Requires the explicit 26.3 HDR main-scene switch. Opaque terrain clears colortex4;
native water/glass records signed surface view distance, opaque view distance
and native legacy fragment depth in RGBA32F. Post checks the corresponding raw
depthtex0/1/2 samples and independently reconstructs distances with its own
gbufferProjectionInverse. Readback uses exact texel centers, never filtered edges.

Only marked translucent pixels are recolored: cyan means a real depth separation
was exercised and all predicates passed; magenta means mismatch; yellow means
insufficient separation, NOT a pass. All other geometry keeps its ordinary view.
The existing deep-pool above-water camera supplies the required visible water
over deeper opaque geometry. The flat shallow comparison scene alone may lack
sufficient separation. No native hand shader is implemented; in this LevelRenderer
scope depthtex1/2 share the pre-translucent image but keep distinct logical IDs.

depth-snapshots-alias-negative substitutes live depth for the final pre-hand
predicate, reproducing the former alias bug without a production fault hook.
depth-snapshots-reconstruction-negative perturbs the post surface reconstruction.
Both MUST show magenta in the same exercised regions, followed by cyan recovery.
This tests numeric data contracts, not full BSL/Iris optical-refraction parity.
