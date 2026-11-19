# Two shadow colors (developer diagnostic, not a visual shader pack)

Writes two different current-frame packed integer markers using reversed
DRAWBUFFERS:10. Both native terrain and final read logical 0 and 1 independently.
Left screen is the post oracle; right terrain is the native oracle.
Green means valid populated markers, magenta means wrong/stale/aliased output,
orange means no populated samples (inconclusive, not success).

At camera Y > 1000 both outputs must instead be entirely zero at sampled points,
checking the per-frame clear with no shadow chunk draws. Return to Y=100 to
resume the populated check. Reload and resize should preserve these rules.

Negative controls (only on a disposable copy, restore afterward): add
`#define NEGATIVE_ALIAS_TEST` after `#version` in final.fsh or
gbuffers_terrain.fsh. This deliberately samples color0 in place of color1.
Only the corresponding oracle must fail; the other must stay green.
