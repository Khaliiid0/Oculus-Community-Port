# Opaque world depth GPU probe

Diagnostic pack only. Opaque terrain is gray. Translucent terrain compares direct
depthtex1 reads with a specialized helper containing a loop/out parameter.

- Orange before final: native direct/helper reads agree and opaque depth is behind
  the fragment. Final converts these pixels to green only when post depthtex1 is
  also behind live depthtex0, proving the two post inputs do not alias there.
- Green: native comparison and independent post opaque/live-depth comparison pass.
- Cyan: reads agree and sampled depth is nearly far/empty.
- Yellow: reads agree and depths are within 1e-6 (contact/precision boundary).
- Red: native opaque depth is closer than the surface, OR final's opaque/live
  separation failed at a native-positive pixel.
- Magenta: direct/helper mismatch.

For a negative control, add 0.01 to the helper result in a copied profile, reload,
verify magenta, then restore. Do not distribute this diagnostic pack as normal
shaders or mistake its colors for intended gameplay rendering.

Post negative control: in a copied profile's final.fsh only, replace the opaque
read `texture2D(depthtex1,texcoord)` with `texture2D(depthtex0,texcoord)`. Formerly
green regions must turn red. Restore and reload to recover green. Native fragment
gl_FragCoord.z is already legacy depth; do not reverse it again in this probe.
