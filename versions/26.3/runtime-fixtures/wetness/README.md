# Wetness transition diagnostic (source-only)

Small corner panel; no full-screen green multiplier. Requires extended/HDR audit.
Default half-lives are explicitly shortened here to 1 s wetting / 2 s drying.
Run rain then clear until both directions and real native terrain/shadows are
covered. Cyan requires coverage and numeric agreement, yellow means incomplete,
magenta means mismatch. Top strips compare raw rain and smoothed wetness.

NEGATIVE_MODE (sidecar #define option): 0 positive, 1 deliberately instant rain,
2 deliberately double-advanced observation. Modes 1/2 must visibly fail during a
transition; a static clear/raining screenshot is not sufficient.

The recurrence is independently persisted in RGBA32F colortex7, with an error
latch. Terrain marker tolerance is 0.00002; shadow is RGBA8 with 0.0021 tolerance
(half UNORM8 LSB plus arithmetic margin), not a fabricated floating-point shadow
target. Final native/post marker mismatch checks are per-frame, not latched.
The reference reseeds after a frame-counter discontinuity; the production filter
deduplicates by frame identity, not counter. A diagnostic reload must reset its
history alongside the production temporal state.

Compilation, wetting/drying on both native backends, both fault observations and
healthy reload/recovery are tested. Post-only and dimension/re-entry qualification
remain pending. See the dated wetness report.
