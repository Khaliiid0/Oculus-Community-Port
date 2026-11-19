# Noise + buffer-lifetime visual fixture

Developer-only three-stage post-processing pack for 0.4.0-preview.2.
The first two stages round-trip the scene through independent colortex3/colortex0
attachments. Final adds very subtle static RGB grain across the entire view using
a generated 128x128 noisetex. No stripe, overlay, split-screen, or diagnostic color bar.

Expected: three passes, two scratch targets, one 65,536-byte noise upload per pack
activation/reload. Noise is not uploaded each frame. This is a functional fixture,
not a performance benchmark or evidence of full terrain-shader support.

For visual acceptance, also use buffer-roundtrip (no grain). Clear the selected
pack and disable shaders in the isolated test configuration after the session.
Fixtures are test resources only and must never be bundled into the mod JAR.
