# Shared camera history (developer diagnostic)

Native terrain and shadow compare their vertex/fragment previous positions.
Terrain uses its own colortex4 payload with alpha=1, so normal scene draws and
translucent alpha blending cannot distort the diagnostic marker. Clear alpha=0
distinguishes untouched pixels. The fixture is not testing pack transparency.
Three post stages compare against native markers. Composite also compares the
previous-position uniform against a current-position marker retained from the
previous GPU frame in colortex6. Colortex7 latches any mismatch, including one
bad frame during movement. Green passes; magenta fails; orange is inconclusive
because no shadow samples are populated. Markers wrap each 32 blocks; use moves
smaller than 32 blocks for the independent temporal check.

The first frame after GPU marker allocation is skipped only for the retained-
marker comparison; native/post comparisons still run. First-frame and world-
identity reset semantics have separate CPU tests. This probe does not establish
all temporal matrix semantics, scene completeness or performance.

In a disposable copy, define NEGATIVE_HISTORY_TEST after composite.fsh's version
line. Stationary it should pass; teleport 8 blocks horizontally and the one-frame
wrong-current-as-previous comparison must latch magenta. Restore and F3+T must
clear the latch and pass again. Test normal movement, reload, resize and dimensions.
