# Post-only wetness cross-check (source-only)

No native world programs, shadow/depth samplers, retained color buffers or
colortexClear=false directives. Three real post passes and their vertices read
wetness; RGBA32F scratch markers check that every consumer sees the same frame.

wetReference uses the existing custom-expression ScalarSmoother, not
WetnessSmoother. Its 100/200 decisecond API fades correspond to the probe's 100/200
wetness/dryness directives (10/20 seconds); CustomExpressionRuntime converts these
to 200/400 internal ticks. The longer lag allows reliable F2 captures.
CPU analytic transition tests qualify
the reference separately. This is a cross-implementation runtime comparison, NOT
a GPU-persisted independent recurrence, and cannot certify errors common to both
implementations. Comparison errors here are per-frame, not latched.

Small corner panel: green=stable agreement; cyan=agreement with active lag;
magenta=mismatch. NEGATIVE_MODE=1 substitutes instant rain in the middle pass and
must fail during a transition; static clear/rain is insufficient. Outside the
corner the ordinary scene is preserved. No full-screen green multiplier.

Use the native wetness fixture's persistent independent recurrence to latch
first-frame world/dimension-reset faults. CPU and call-site tests cover post-only
epoch reseeding; do not infer first-frame GPU qualification from a later photo.
