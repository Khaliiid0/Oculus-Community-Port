# Additional 26.3 developer controls

These are authored diagnostic fixtures, not BSL/Complementary or distribution assets.
Use only the isolated runtime-audit client and disposable Superflat Creative world.

- extended-normal: ordinary textured terrain when normal/tangent length,
  orthogonality, handedness magnitude, mid-UV bounds and mapped
  grass/dirt/bedrock material ranges pass. Invalid inputs give magenta.
  This does not check exact mid-UV values or every attribute on complex geometry.
  Requires extended-terrain admission.
- extended-negative: same test with a deliberately impossible material ID range;
  must produce magenta, then be replaced with the positive control.
- hdr-scene: tests raw (-.5,2,4,1.5) terrain values before presentation.
  Green means preserved, magenta means incorrect/clamped, orange means no coverage.
  Requires HDR-main-scene admission.
- hdr-negative: deliberately changes one raw channel; must produce magenta.
- hdr-inspect: the original broad predicate's first failing grid sample is
  encoded into four grayscale R/G/B/A bands. This is approximate RGBA8 output,
  not lossless readback. Used to distinguish mixed scene pixels from raw markers.
- hdr-localize: spatial view of that predicate; exact terrain is green,
  invalid marker pixels red, and other world rendering is diagnostically scaled.
- native-normal-hdr: ordinary texture/tint/lightmap through an R11F_G11F_B10F
  main scene and a single final pass. No complex pack water/depth/material inputs.
  It keeps water visible in the retained stone/leaf/shallow-pool scene. Before
  the deferred-order correction the real packs failed this comparison; that
  specific regression now passes. This remains a differential control,
  not proof that real-pack water or every HDR interaction works.
- deferred-distance: an authored deferred producer writes its `far` value and
  current-frame marker into RGBA16F colortex4. Opaque terrain overwrites the
  marker with zero first. Visible ordinary water means that native water saw
  the deferred marker and the same distance value. Magenta water is failure;
  no water means this predicate was not exercised. No green view multiplier.
- deferred-distance-negative: deliberately writes half the deferred distance;
  visible water MUST turn magenta, then recover to the positive control.
- water-scene: a manually invoked developer DATAPACK (not a shader pack),
  restricted to the disposable audit save. Provides bounded deep-water geometry,
  contrasting floor, stable above/underwater cameras and dimension observations.
  No load/tick functions. Its pinned metadata parses through Minecraft's actual
  26.3 data-pack codec. See its README before installation or use.

The positive/negative HDR oracles now align their 16x16 grid to actual texel
centers using viewWidth/viewHeight. Runtime color sampling is LINEAR: the old
normalized grid could interpolate between terrain and a neighboring scene pixel,
creating a false failure even when both texels were correctly rendered. This
correction does not exclude actual blended overdraw; use a clear terrain view.
All oracle results cover sampled visible terrain, not every world fragment.

Restore the normal-view fixture or disable shaders after oracle checks.
No passing color is proof of complex scenes, shadow accuracy or full pack support.
