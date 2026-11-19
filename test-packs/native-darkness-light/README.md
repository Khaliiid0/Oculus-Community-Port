# Extracted lightmap Darkness diagnostic — Forge 26.2

Green/yellow-green means sampled native terrain/shadow inputs agree with the
independent post bindings; red intensity follows the actual lightmap pulse.
Magenta means a mismatch or failed expectation. Orange means missing sampled
terrain/shadow coverage. RGBA8 markers preserve both good and bad samples and
exclude unrelated opaque colors. This is a diagnostic, not pack visuals.

EXPECT_CLEAR requires both effect values to be zero. EXPECT_ACTIVE requires the
raw Darkness blend near one and the lightmap pulse to differ from it.
EXPECT_PULSE additionally requires an actually nonzero pulse above .08; during
the genuine dark half of the cycle this intentionally fails, not a renderer bug.
NEGATIVE_DARKNESS_VERTEX injects a vertex-only mismatch. Use only an isolated copy
of the test world; clear the effect and remove temporary controls after testing.
