# Normal-view buffer round-trip check

LGPL-3.0-only, like this repository. A developer test fixture, not a shader enhancement.

Expected image: the complete normal scene, with no stripe, border, marker or split screen.
The renderer must remain active: this is not a shaders-disabled test.

deferred writes a channel-rotated scene to colortex3. composite reads BOTH the original
colortex0 and the independent colortex3 (via its composite alias), reverses the channel
rotation, and averages the matching images. final presents colortex0. Correct routing
preserves the original colors; collapsing those two inputs can cause an obvious color cast.

Use this for normal visual acceptance checks, including both screen edges and HUD.
The separate buffer-routing fixture's visible diagnostic must be explicitly enabled.
Restore shaders.enabled=false and clear the selected fixture in the isolated run config
when finished. This does not prove BSL/Complementary compatibility or improved performance.
