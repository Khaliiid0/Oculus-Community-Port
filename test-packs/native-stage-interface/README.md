# Native modern-stage-interface diagnostic

Developer fixture, not a distributable shader preset. Terrain and shadow both
exercise modern flat integer/grouped vector varyings, smooth float vectors,
centroid/noperspective declarations and live in/out/inout helper parameters.
Constant stage values check preservation and real native compilation/linking;
they do not establish numerical interpolation accuracy over varying geometry.

Owned colortex4 and shadowcolor0 carry opaque markers. Green requires populated
terrain and shadow sample grids with the expected payloads. Orange means missing
sampled geometry (inconclusive), magenta is a mismatch. Only a 4x4 shadow/terrain
grid is checked, not every pixel. Colors are intentional diagnostic output.

Add #define NEGATIVE_STAGE_VALUE at the top of the disposable vertex include and
reload: an incorrect flat integer value must turn the diagnostic magenta. Restore
the include byte-for-byte and reload to recover green. Check reload and resize.
CPU tests independently reject type/interpolation mismatches, unknown mesh inputs,
arrays/blocks/layout-qualified varyings, invalid qualifiers and duplicate names.
