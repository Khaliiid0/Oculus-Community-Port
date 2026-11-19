# Temporal reset diagnostic (developer only)

Post-only root/fallback shader. Left half: current rain as grayscale (black dry,
white rain 1). Right half: accumulated wetness (green below .1, yellow .1–.25,
magenta above .25). Magenta here means established history, not a rendering error.

Use an isolated rainy world. Wait until right side is magenta, then leave/re-enter
without reloading the pack: right should start green while rain remains white.
Wetness rises again after entry, so observe immediately. Reload is not a substitute
for logout or dimension-transition coverage. Repeat with a dimension change.
This probe does not certify matrices, velocity, exact half-life or GPU textures.
