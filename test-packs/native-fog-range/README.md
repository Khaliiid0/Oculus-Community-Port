# Native and fullscreen environmental fog diagnostic — Forge 26.2

Green means native terrain/shadow values match the independent post reads and
native/fullscreen stage interfaces agree. Magenta means a mismatch or failed
expectation. Orange means missing sampled coverage. Native fog alpha must be the
actual color alpha, not the sun-angle value sharing the old RGB lane.

Terrain uses colortex4 for range/failure markers and colortex6 for actual RGBA;
shadowcolor0/1 hold the independent shadow results. Quantized range markers use
modular encodings, not an arbitrary-precision transport. CPU tests check exact
raw floats, reversed/equal/tiny spans and signed/modded values. The live fixture
requires ordinary increasing ranges and RGBA colors representable in RGBA8.

EXPECT_MEDIUM, EXPECT_START and EXPECT_END provide optional known-input assertions.
NEGATIVE_FOG_VERTEX injects a terrain vertex-only mismatch. Use an isolated copied
test world and remove temporary controls after testing. Colors are diagnostics,
not shader-pack image-quality evidence.
