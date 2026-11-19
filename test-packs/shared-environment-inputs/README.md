# Shared environment-expression diagnostic

Developer fixture, not a distributable visual preset. The selected property table
declares the exact maxBlindnessDarkness identity plus smoothed rain, raw eye-light
and frame-time expressions, an effect alias dependency and smoothed snow membership.
Terrain/shadow vertex and fragment compare custom values; post independently reads
the same variant's values and the native effect semantic. Opaque owned colortex4/6
and shadowcolor0/1 carry separate payloads. Green requires populated matching
terrain/shadow samples; orange is inconclusive; magenta is a mismatch.

The marker format is RGBA8: frame time uses t/(1+t) to stay bounded without
clipping a legitimate long frame. This is not a performance or frame-spike test.
Only a 4x4 grid plus the local terrain pixel is checked, not all shadow pixels.
CPU tests independently establish exact smoothing response and reset behavior.

In the disposable copied world, use /weather rain 600s (the s suffix matters;
a bare 600 lasts only 30 seconds) and a small /fillbiome region
around the camera set to minecraft:snowy_plains, and give the player blindness.
Enable REQUIRE_RAIN, REQUIRE_SNOW and REQUIRE_EFFECT at the top of disposable
final.fsh to require genuinely nonzero inputs, then reload. This reload seeds
smoothing at the current state; it does not independently time a GPU transition.

Negative control: add NEGATIVE_SHARED_VERTEX above the include in disposable
vertex.glsl and reload. A wrong vertex rain value must produce magenta. Restore
all fixture files byte-for-byte and reload to recover green; then check resizing.
Effects/weather/biome changes must never target a normal user world.
