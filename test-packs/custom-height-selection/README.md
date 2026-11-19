# Native height selection probe

Not GPU verified. See ../../NATIVE_HEIGHT_SELECTION_PROGRESS.md.
Left is terrain, right shadow.
Green means native vertex/fragment values agree with post camera values; magenta
means mismatch, black means no marked geometry and is not a pass. Tolerance .006
allows RGBA8 quantization. This is a consistency check, not an independent proof
of absolute world-space camera accuracy.

The marker now requires alpha .375 as well as blue .25/.75. The original alpha=1
probe could mistake opaque player colors for test geometry in third person.
This remains heuristic: blended/translucent pixels may lose the marker and are
not certified by this probe. It tests visible marked terrain/shadow, not entities.

On an isolated copied world test camera heights around 113, 153 and 193 (below,
at and above each threshold). Player teleport Y is not necessarily camera Y. Test
first/third person,
reload and world return. Set staged final expectedOffset=.1 for a negative control,
restore zero and reload afterward. Do not modify the normal player profile.

Native properties sum three if(comparison,1,0) gates; post uses independent GLSL
if statements over the camera snapshot. Red encodes the gate count divided by 3;
green encodes altitude/512. No biome classification or stateful smoothing is tested.
The post stage does not use custom uniforms. General boolean support is not claimed.
