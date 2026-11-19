# Native/post environment agreement GPU probe

Native terrain encodes medium/3, darkness and fog-red into RGB. The final stage
compares it with post uniforms using 0.015 tolerance for RGBA8 quantization.
Passing terrain is orange in air, blue in water, red-orange in lava, pale blue in
powder snow, or purple under darkness. Magenta marks disagreement. Non-terrain
pixels (sky/particles) are not encoded and are not assertions.

Negative control: add 0.1 to native fog-red in a copied profile, reload and verify
magenta, then restore. Air/water/darkness transitions were GPU-tested; lava/snow
mapping has CPU coverage only. These are test markers, not gameplay shaders.
