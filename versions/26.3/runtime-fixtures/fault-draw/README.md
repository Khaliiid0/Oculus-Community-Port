# Source-only deferred exception audit: healthy HDR prototype

Authored local developer fixture. Ordinary textured water means the native water
pass saw this frame's deferred marker AND matching `far` values. Magenta water
is failure. View a visible water surface; no water means the predicate was not
exercised. Opaque geometry writes a zero marker before deferred, avoiding stale
previous-frame data as a passing control. The marker is RGBA16F.
The predicate applies to all terrain routed to gbuffers_water, including the
test platform's glass, not just fluids.

Unlike the RGBA8 distance control, this uses an HDR main scene and two deferred
scratch outputs. The fault-* copies are byte-identical shader sources; ONLY
the separate src/faultAudit mixins inject one exception per point/backend when
their exact audit pack IDs are selected. Without that explicit source harness,
all copies are ordinary healthy controls. No fault machinery is in main sources.

Injection points: second view allocation, after a completed/closed deferred draw,
after whole-batch validation, and after the first issued auxiliary copy (main
scene copy is last). These are bounded Java faults, not driver/context-loss or
out-of-memory experiments. Issued copies are NOT claimed to roll back atomically.
The harness checks usable main-pass restore, active generation retirement, held
HDR framebuffer lease until frame end, then zero tracked allocations.
