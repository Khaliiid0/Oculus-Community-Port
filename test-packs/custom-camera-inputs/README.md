# Native camera expression probe

Scoped GPU checks on October 6; see ../../NATIVE_CAMERA_INPUTS_GPU_PROGRESS.md.
Left is terrain, right shadow.
Green means native vertex/fragment values agree with post camera values; magenta
means mismatch, black means no marked geometry and is not a pass. Tolerance .006
allows RGBA8 quantization. This is a consistency check, not an independent proof
of absolute world-space camera accuracy.

The marker now requires alpha .375 as well as blue .25/.75. The original alpha=1
probe could mistake opaque player colors for test geometry in third person.
This remains heuristic: blended/translucent pixels may lose the marker and are
not certified by this probe. It tests visible marked terrain/shadow, not entities.

On an isolated copied world test several heights and X/Z positions where the
encoded values remain unclamped (e.g. Y=64..256, X/Z=-128..128), first/third person,
reload and world return. Set staged final expectedOffset=.1 for a negative control,
restore zero and reload afterward. Do not modify the normal player profile.

Property components are limited to cameraPosition.x/y/z; no general vector or
boolean expression support is claimed. The post stage does not use custom uniforms.
