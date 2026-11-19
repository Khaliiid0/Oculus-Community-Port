# Native scene precision diagnostic

Forge 26.2 development fixture, not a user shader pack. Terrain writes
(-0.5, 2, 4, 1.5) into logical colortex0 with RGBA16F metadata. The independent
post stage checks terrain coverage and all four raw channels before display.
Green=preserved, magenta=wrong/clamped, orange=no terrain coverage. The old RGBA8
scene must fail magenta; the opt-in HDR scene must pass green. Optional
NEGATIVE_HDR_SCENE changes green to 1 and must fail. Use the copied stone-platform
world at (0.5,113,0.5), yaw 0/pitch 60, with improved transparency disabled.

Tests actual native-to-post precision, not a screenshot's ability to store HDR,
nor complete material/water/sky/hand rendering. No tone mapping is invented.
