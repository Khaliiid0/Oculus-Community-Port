# Controlled shadow occlusion probe

Developer diagnostic, excluded from mod artifacts. In a disposable copied world:

1. `/fill 56 200 -40 72 201 -24 minecraft:stone`
2. `/fill 63 202 -33 65 210 -31 minecraft:stone`
3. `/tp @s 64.5 217 -31.5 0 90`
4. Fix time/weather for each capture, then select this pack.

The final pass displays a zoom of the shadow map. It reconstructs each sampled
texel's light ray and intersects the two analytic boxes above. The nearest hit
must match both live/opaque depth maps within 0.00003. The fragment encodes its
legacy depth as a 16-bit value across two RGBA8 channels; the decoded marker must
match within 0.00002. A single 8-bit channel produced quantization stripes at the
original 0.0021 threshold on this GPU. Green is a passing
floor sample; cyan a passing pillar sample; gray a correctly empty ray. Magenta
is a depth/marker mismatch, red unexpected geometry, orange out of map coverage,
and yellow a view direction that fails to align with the daytime sun.
No hit coverage means no successful occluder test.

For a deliberate negative control change expectedOffset in the copied profile
to 0.05, reload, then restore 0.0. Passing hit regions must become magenta and
recover. Changing the sun time tests different light-space directions. This
checks the renderer's supplied shadow transform, daytime light alignment and
depth ordering. Pack shadow distortion, soft shadows, bias tuning, offscreen
casters and full scene ordering require separate coverage.
