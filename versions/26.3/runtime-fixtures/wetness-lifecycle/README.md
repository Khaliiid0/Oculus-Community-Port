# Native wetness lifecycle diagnostic (source-only)

Fork of the qualified wetness diagnostic; older evidence/source is untouched.
This probe uses 10-second rise / 20-second fall half-lives, retaining a detectable
lag across dimension loading if production state incorrectly leaks across worlds.

An independent RGBA32F recurrence reads/writes pixel (0,0) of retained colortex7.
Other pixels are zeroed; the final panel always reads (0,0). Consecutive numeric
frame counters wrap at 720720, matching AnimationClock; a missing prior marker
means first sample must equal real rain. A first-frame recurrence mismatch is
latched, so it cannot heal before a screenshot. Native final marker checks remain
per-frame and shadow marker precision is explicitly RGBA8.

Magenta is a mismatch. Yellow means not all normal transition/geometry coverage
has been exercised in this epoch; it is not a failed reset. Cyan requires both
wetting/drying lag plus real terrain/shadow markers. Validate epoch reseeding by
entering a different-rain world while wetness is visibly lagging, then returning
to the original world. Re-entry/reload must not inherit the previous epoch.
NEGATIVE_MODE=3 perturbs only the first observed frame by 0.5. A later magenta
capture must remain latched despite correct subsequent production samples;
healthy reload must recover. This is an oracle fault, not a production mutation.
