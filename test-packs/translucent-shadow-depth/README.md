# Transparent terrain shadow-depth diagnostic

26.2 extended-terrain diagnostic, fixed 512 shadow resolution; not an appearance pack.
Samples both depth maps at the same 8x8 grid of integer texels. Mode 0 requires some
closer live-depth samples and some preserved opaque samples; green passes, red means
no separation, orange means insufficient opaque coverage, magenta means depth order
is wrong. This checks samples, not every texel or full colored-shadow behavior.

Reproduction used the copied BufferRoutingTest world at (64.5,160,-31.5), looking down,
near noon, with `/fill 40 144 -56 88 144 -8 minecraft:red_stained_glass` (2401 blocks).
Only perform this in a disposable copied world, never a user's normal save.

Controls in shaders/shaders.properties:

- Both true, final expectedMode=0: green separation.
- shadowTranslucent=false, shadowTerrain=true, mode 0: red negative control.
- shadowTerrain=false, shadowTranslucent=true, mode 2: cyan if sampled opaque depth
  is empty and sampled live depth is populated; orange if no live coverage.
- Both false, mode 3: cyan if both sampled maps are empty, magenta otherwise.

Restore both true and mode 0, reload with F3+T, require green recovery.
The shadow shader writes diagnostic depth/frame markers without an atlas alpha test.
It validates transparent layer submission and snapshot separation, not texture
transparency, colored shadow blending or multi-output shadows.
