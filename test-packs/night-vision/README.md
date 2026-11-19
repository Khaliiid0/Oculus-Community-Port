# Night-vision snapshot diagnostic

Terrain and shadow store the nightVision value in red and a vertex/fragment mismatch flag in green. Final compares both against the post snapshot and expectedNightVision (initially 0). Left is terrain; right is shadow. Black means no geometry marker, green pass, magenta mismatch. This is not an artist shader.

Blue .25 marks diagnostic geometry (tolerance .01 for RGBA8 quantization). Use a dry world: rain and other vanilla draws can modify color 0 after terrain. The marker is a scoped diagnostic signature, not a general ownership mask; sky/weather/entity rendering is not certified by green marked pixels. An earlier blue=1 marker collided with bright sky pixels and was replaced.

Test no effect, long-duration night vision (expected 1), removal (expected 0), a deliberately wrong expected value and recovery. For fading values, use compareExpected=false to check per-frame stage parity without asserting a fixed value. The range check still applies. Restore defaults afterward. Camera-entity spectating and effect expiration need explicit GPU coverage; compilation does not verify them.
