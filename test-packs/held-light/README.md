# Held-light diagnostic (developer-only)

Terrain RGB encodes main-hand light / 15, constant green .25, and offhand
light / 15. Vertex and fragment values must agree; disagreement is magenta
with green zero. Empty hands are dark green, main-hand torch orange/red,
offhand torch blue. This is not artistic lighting or a dynamic-light mod.

Default oldHandLight=false checks independent hands. Temporarily set true and
reload to test legacy max(main,off) behavior. For exact torch checks with a
torch in the offhand and empty main hand, assert values == vec2(14,14) in
legacy mode. Swapping it back to the main hand must fail that assertion.
Restore source files after controls. Use only a copied disposable world.

This fixture covers native terrain vertex/fragment inputs, not native shadow
held-light bindings, post held-light support, or special modded item providers.
