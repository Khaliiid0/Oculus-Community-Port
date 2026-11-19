# Native custom time-curve probe

Scoped GPU verification on 2026-10-06: see ../../NATIVE_CUSTOM_TIME_CURVES_GPU_PROGRESS.md.
Only four frozen times sampled; dawn shadow coverage remains unverified.
Terrain and shadow encode property-declared time curves in
RG; final checks against an independently piecewise-written fade reference and
clock-derived daylight sine. Left: terrain, right: shadow. Green agreement,
magenta mismatch, black unmarked. Marker detection is heuristic; tolerance .006
allows RGBA8 quantization and small transcendental differences.

Test times 0, 6000, 12330, 12445, 12560, 13010, 13120, 13230, 18000, 22770,
22880, 22990, 23440, 23555, 23670. Freeze daylight advancement in the disposable
world for boundary checks. Set expectedOffset=.1 for a negative control, then
restore zero. Custom property names intentionally differ from BSL's names.
