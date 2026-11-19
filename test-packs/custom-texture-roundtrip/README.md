# Custom PNG round-trip fixture

Three stages, two scratch targets and a single shared 1x1 RGBA white PNG (4 GPU bytes).
Composite uses a stage-bound colortex5/gaux2 override; final uses a named custom sampler.
Both bindings refer to the same decoded/uploaded image. Multiplication by white preserves
the normal full scene without stripes, overlays or split screens. The final pass also uses
viewWidth/viewHeight for a half-texel edge clamp, exercising one owned uniform ring and
its resize updates.

This validates a supported subset, not full shader-pack compatibility or performance.
The image is deterministic test data; no third-party texture or shader code is included.
