# Shared eye-light smoothing GPU probe

Diagnostic only, not an artistic shader. Default half-life 400 ticks (20 seconds)
deliberately exposes transitions long enough for manual observation.

Bottom region: terrain on left, shadow on right; black is unmarked background.
Green means native vertex/fragment/post agreement, cyan means agreement while
raw and smoothed values differ by more than 16, magenta means mismatch.
Top four bars: raw block, smooth block, raw sky, smooth sky (top downward).
Full width represents 240. Marker matching is heuristic, not a general ownership mask.

In a disposable world, introduce nearby block light, move away, check both channels,
reload, switch dimensions and re-enter. Set forceMismatch=true for a negative control.
Change eyeBrightnessHalflife to zero for immediate updates. Restore defaults afterward.
Equal values alone do not prove smoothing; require a visible delayed bar transition.
