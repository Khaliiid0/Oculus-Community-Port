# Local-only stage bootstrap

Unlike the manual lighting controls, this diagnostic-only pack has a LOAD tag.
On loading the separate `bank-stage-20261118` copied disposable world it sets
clear daylight, holds time and places the local test player at the recorded
river camera. No block edits. Do NOT install in a user's normal world or a
normal mod distribution. It is used only with the unchanged checkpoint JAR's
existing bounded, one-frame stage-capture JVM properties.

Captured PNGs are clamped RGBA8 stage previews, not lossless HDR measurements,
FPS evidence or a reference Iris implementation. Restore advance_time before
exit; the LOAD tag deliberately reapplies the diagnostic setup on another boot
of this isolated copy.
