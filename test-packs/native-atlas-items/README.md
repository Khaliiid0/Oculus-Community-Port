# Live block-atlas and held-item diagnostic (26.2)

Green means sampled native terrain/shadow data agrees with independent post-pass
item bindings, native vertex/fragment values agree, and atlas dimensions match
the actual bound gtexture dimensions. Orange means the sampled grid lacks terrain
or shadow coverage; magenta means a mismatch, missing mapping or failed expectation.
The test requires mapped items (air, torch, glowstone, oak log/planks).

EXPECT_MAIN and EXPECT_OFF optionally assert exact post IDs during live controls.
NEGATIVE_ITEM_VERTEX in gbuffers_terrain.vsh injects a vertex mismatch. The RGB
payload is for these small test IDs, not a transport for arbitrary signed IDs.
The production buffer uses exact signed integers; CPU tests cover both extremes.
These colors are diagnostics, not shader-pack visuals or release qualification.
