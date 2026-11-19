# Native sky-color diagnostic

Forge 26.2 development fixture, not a user shader pack. Compares the captured
camera-environment sky RGB between native terrain vertex/fragment, native
shadow vertex/fragment, and independent post vertex/fragment bindings. Terrain
and shadow coverage are required. Green=pass, magenta=mismatch, orange=coverage
missing. Temporary NEGATIVE_SKY_VERTEX adds .125 red and must fail; optional
REQUIRE_NONZERO_SKY rejects a constant black daytime stand-in. SHOW_SKY_RGB
displays the post snapshot to check day/night changes separately from parity.

No normal scene appearance or full BSL/Complementary qualification is implied.
