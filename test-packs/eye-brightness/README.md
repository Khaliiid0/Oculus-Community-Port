# Raw eye-brightness diagnostic

Left: native terrain; right: native shadow. Red/green encode block/sky lightmap values (0–240). Blue .25 marks agreeing native stages, .75 marks a vertex/fragment mismatch. Final checks both native values against the post snapshot and an explicit expected pair, initially (0,240). Unmarked pixels are black, passing geometry green, failures magenta.

Use a dry isolated world away from other draws. Test outdoor sky light, a dark enclosed location, block light, a deliberately incorrect expected pair and recovery, plus dimension/re-entry. Change only staged expectedBrightness for each known location. The marker is a scoped diagnostic signature, not a general ownership mask. No eyeBrightnessSmooth claim: smoothing and its pack half-life remain unimplemented. CPU fixture preparation is not GPU validation.
