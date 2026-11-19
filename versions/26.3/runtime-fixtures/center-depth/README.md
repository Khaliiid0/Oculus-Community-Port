# GPU center-depth control (source audit only)

Use the isolated audit world, looking at opaque geometry farther than interaction
reach (for example `/tp @s 8.483676 -60 -10 0 0`). Both fullscreen stages read the
same GPU sample, with half-life zero so it can be compared to current rendered
depth. Top-left panel: cyan = exercised geometry and match, magenta = mismatch,
white = matching sky (not the out-of-reach geometry assertion). The rest of the
view is unchanged. Do not point this post-only control at translucent surfaces:
its final reference is live depth, whereas center depth is sampled before them.

`center-depth-native` instead compares the opaque `depthtex2` snapshot and also
exercises native terrain. `center-depth-negative` deliberately substitutes 1.0,
the old interaction-ray MISS estimate; it must turn magenta on distant geometry.
These diagnostic packs are not distributable shader packs or release evidence
without a recorded runtime run. Restore the original camera with
`/function oculus_audit:restore`.

`center-depth-smooth` independently reconstructs each expected smoothed value
using a persistent RGBA32F post buffer and the previous completed frame's result.
It uses half-life 10 (reference Iris decay: one second), checks both fullscreen
stages against the same sample, and latches any numeric mismatch until reload.
Move between geometry and sky to exercise history, not only a steady state.
`center-depth-smooth-negative` deliberately requests no smoothing while keeping
the one-second reference equation: after changing the view, it must latch magenta.
