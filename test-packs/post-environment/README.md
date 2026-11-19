# Post-only environment diagnostic

Developer fixture, not a visual shader pack. No terrain or shadow program.
Entire final pass is green when moonPhase/rainStrength match literal expectations
and rainFactor agrees with rainStrength, magenta otherwise. Defaults expect phase
7 and dry weather. Set time to 174000 in an isolated Overworld for that phase.
Test wrong expectations, reload, rain endpoints, world re-entry and shutdown.
This checks binding availability without native draws; it does not test native
parity, smooth wetness history, interpolation timing or artist-pack compatibility.
