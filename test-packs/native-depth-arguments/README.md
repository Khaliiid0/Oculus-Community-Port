# Seventh-argument depth helper diagnostic

Developer fixture derived from native-opaque-depth. The same loop/out-parameter
helper reads depthtex1 at argument seven and noisetex at that same position.
Nested constructor/function arguments test real argument-boundary handling.
The native water shader requires both helper/direct comparisons to agree: depth
must reverse once, ordinary noise must not reverse. Post independently checks
that the opaque snapshot lies behind the live water depth on backed-water pixels.

Use a copied fluid/depth-control world. Backed-water regions are green; magenta
means a native helper mismatch, red a failed post depth ordering. Cyan/far-depth
and yellow/contact classifications are not green passes. This is a visual oracle,
not quantitative depth readback or full reflection/refraction qualification.

Negative control: add NEGATIVE_SEVENTH_DEPTH at the top of disposable
gbuffers_water.fsh, reload, require the previously green region to turn magenta.
Restore byte-for-byte, reload, require green, then test resizing. Do not edit a
normal user world or install this diagnostic as a visual preset.
