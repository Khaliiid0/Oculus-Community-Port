# HDR and signed-normalized MRT round trip

26.2 development-only fixture. Stores scene RGB outside the normalized range in RGBA16F
and probes simultaneous RGB8_SNORM, RGB16F and RGB8 attachments, then reconstructs
the scene. RGB probes also verify implicit alpha one despite non-one shader outputs.
A normal scene is the pass result; solid magenta means a storage probe failed.
No debug stripe or normal-game overlay is added. Requires the format-aware renderer.
