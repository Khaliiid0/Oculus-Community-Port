# Deferred distance handoff: positive control

Authored local developer fixture. Ordinary textured water means the native water
pass saw this frame's deferred marker AND matching `far` values. Magenta water
is failure. View a visible water surface; no water means the predicate was not
exercised. Opaque geometry writes a zero marker before deferred, avoiding stale
previous-frame data as a passing control. The marker is RGBA16F.
The predicate applies to all terrain routed to gbuffers_water, including the
test platform's glass, not just fluids.

The negative sibling deliberately writes half the post `far`; it MUST produce
magenta water before recovering to this fixture. These are not real shader-pack
assets, normal graphics, an FPS comparison, or full renderer qualification.
