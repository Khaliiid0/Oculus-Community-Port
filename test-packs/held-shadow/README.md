# Shadow held-light diagnostic

Developer fixture, excluded from production. Native shadow writes main/offhand
light into RG bytes, vertex/fragment disagreement into B, and occupancy into A.
The final pass samples shadowcolor0 and compares occupied pixels against
expectedMain/expectedOff constants: green passes, magenta fails, black is empty
and is NOT evidence of a pass. This is a diagnostic map, not artistic shading.

Default expectation is 0/0 with oldHandLight=false. In a copied world, give a
redstone_lamp item with minecraft:block_state={lit:"true"}; main/off expectations
are 15/0 or 0/15 depending on the hand. Swapping hands without changing the
expectation is a negative control. The lit:"false" component restores emission 0.
Restore final.fsh constants to zero and reload after testing.
