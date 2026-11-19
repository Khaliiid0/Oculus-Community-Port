# Native/post cloud-height diagnostic

Developer-only fixture for the unmodified copied Overworld. Native terrain and
shadow vertex/fragment stages compare the camera-extracted cloudHeight; final
compares their opaque colortex4/shadowcolor0 payloads to the independent post
uniform and the base game's expected 192.33 height. It does not test modded
attribute overrides, spatial interpolation, alternate dimensions or real clouds.

Green requires populated terrain and shadow markers; orange is inconclusive
(missing sampled geometry), magenta is an input/parity mismatch. A 4x4 grid
samples each producer; this is not exhaustive image correctness.

Negative control: add #define NEGATIVE_CLOUD_VERTEX after #version in the
disposable gbuffers_terrain.vsh. Reload must produce magenta; restore the source
byte-for-byte and reload to recover green. Check resizing after recovery.
