# Post-only shared eye-light probe

No native terrain/shadow programs. Three post stages encode and compare the shared
smoothed value within one frame (RGBA8 comparison tolerance: one lightmap unit).
Final output is intentionally full-screen diagnostic color, not a normal shader.
Green is agreement, cyan is agreement while raw/smooth differ by more than 16,
magenta is mismatch. Top bars: raw block, smooth block, raw sky, smooth sky.
Half-life is 400 ticks / 20 seconds, declared only in composite.fsh.
Set final.fsh forceMismatch=true as a negative control; restore afterward.

Stronger data-path control: at an unlit camera position, temporarily encode
`eyeBrightnessSmooth+ivec2(32,0)` in composite.fsh, leaving forceMismatch=false.
Both subsequent comparisons should detect the altered block-light value. Restore
the original encoding afterward. This checks data mismatch detection rather than
merely forcing the final diagnostic color.
