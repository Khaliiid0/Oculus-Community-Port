# Native shadow-color diagnostic (developer-only)

This pack is excluded from production JARs. It is not an artistic shader pack.

At ordinary camera heights, terrain probes a 4x4 grid of shadowcolor0 texels:
green tint means occupied samples match the current frame counter, orange means
the grid found no occupied samples, and magenta means a counter mismatch.
The final pass checks occupied shadow pixels across its screen UVs too.

At cameraPosition.y > 1000, the final pass instead expects every sampled RGBA
component to be zero: green (0,1,0,1) means clear, magenta (1,0,1,1) means nonzero.
Use spectator mode in an isolated copied world to hold a stationary camera at
y=3000, beyond terrain/shadow range. Vanilla vignette can darken screen edges.
Previously this branch encoded success as cyan (0,1,1,1); that was a probe
color mistake, not a shadow-map failure.

Negative controls: change expectedOffset to 1 to test counter mismatches, or
temporarily add vec4(.01) to marker inside the empty-map comparison to test its
failure signal. Reload with F3+T after edits. Restore all fixture files and
return the camera/game mode afterward. Do not count a control as a production fix.
