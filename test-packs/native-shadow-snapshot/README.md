# Native opaque shadow-depth diagnostic

26.2 extended-terrain test only; not an appearance shader or supported real pack.
Native terrain reads raw shadowtex0 and comparison-helper shadowtex1. Shadow color
marks actual reverse depth and frame count. Fullscreen texelFetch reads both depth
snapshots and the marker at the same pixel (fixed 512 shadow resolution).

Green marked terrain means agreement, magenta means failure, orange means inadequate
coverage. Opaque-only shadow rendering currently makes both maps equal; this probe
does not prove translucent exclusion. GPU checkpoint tested on AMD Radeon Graphics.
shadowTranslucent=false deliberately keeps this equality probe opaque-only.
Set copied-world time to 6000 if the coarse 8x8 coverage grid misses interior depth.
Do not count orange or unmarked scene regions as passes.

Separate negative controls: change expectedOffset from 0 to .125 in terrain or final,
then reload with F3+T. Restore 0 afterward. Terrain control changes marked terrain;
fullscreen control overlays the whole scene. Final uses exact texel markers to avoid
mixing linear color sampling with nearest depth at geometry edges.
