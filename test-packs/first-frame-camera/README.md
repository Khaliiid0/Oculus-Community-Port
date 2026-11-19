# First-frame camera latch (developer diagnostic)

Native terrain writes only color 0. Composite writes retained color 7, initialized
transparent black, and only evaluates its checks while that buffer is uninitialized.
It checks previous/current camera fractions, zero velocity, and previous/current
projection/view matrices. It deliberately never declares full previousCameraPosition.
Later frames preserve the first result. Final is green pass, magenta fail, black
if no initialized marker was produced. This makes a one-frame failure inspectable.

Use a noninteger camera location (including negative coordinates), world entry and
dimension changes. Set staged forceFailure=true and reload for a negative control;
restore false and reload for recovery. Re-entry must reset retained GPU storage as
well as CPU history. A resize also resets this buffer but need not reset CPU camera
history, so do not treat arbitrary mid-motion resize as a first-world-frame check.
Not an artist pack or performance benchmark; does not check inverse matrices.
