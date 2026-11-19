# Native projected varying diagnostic

Terrain vertices calculate clip positions using both named and legacy built-in
projection matrices, then pass them as perspective interpolated varyings. The
fragment compares them with its legacy fragment coordinates and reconstructs
view position using the fragment inverse projection.

Green terrain passes all checks; magenta fails. The raw vertex/reversed depth
contract from the previous checkpoint fails the projected-depth comparison.
Change expectedOffset in a copied profile to 0.05 for a failure control, then
restore 0.0. Sky, entities and hands do not run this probe. These flat diagnostic
colors do not demonstrate real shader-pack appearance.
