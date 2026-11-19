# Native environment GPU probe (developer only)

Not a visual shader pack or release asset. Left half shows terrain output; right
half shows the full shadow map. Populated matching texels are green, mismatches
magenta, empty shadow texels black. Non-terrain pixels on the left are not evidence.
Both native vertex stages export values checked against their fragment-stage inputs.
The final pass checks encoded values against explicit expectedMoon/expectedRain
constants and additionally compares native output against post moon/rain uniforms.
Literal expectations remain independent so equal-but-wrong values still fail.
Earlier GPU evidence used the fixture before this post-parity extension.

Defaults expect moon phase 0 and dry weather. Set time/weather in an isolated test
world. Change only the staged final-pass constants for other cases; use F3+T to
reload. A deliberate wrong expectation must turn populated areas magenta before
returning to green. Allow rain transitions to settle for endpoint checks. This
8-bit color probe tolerates .01 rain error and does not certify interpolation.
Check several moon phases, weather endpoints, dimensions, reload and cleanup.
