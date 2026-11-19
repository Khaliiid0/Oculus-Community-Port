# Native helper-pruning probe

Developer-only, not a distributable shader pack or a normal visual preset.
Terrain and shadow vertex/fragment sources include an unreachable two-function
chain that references an unsupported vertex texture and unknown library input.
They must prepare without either input. A separate transitive, live helper chain
must remain and produce matching vertex/fragment markers in both native passes.

Green means populated terrain and shadow markers match; orange is inconclusive
(at least one sampled producer is empty); magenta is a mismatch. Payloads use
owned colortex4 and opaque alpha so scene contents and blending cannot act as a
false numerical oracle. A 4x4 grid samples each producer, not every shadow pixel.
The test is not a general image-quality or pack-compatibility certification.

Negative control: add `#define NEGATIVE_VERTEX_MARKER` above the include in the
disposable profile's vertex.glsl and reload; populated markers must fail magenta.
Restore the exact fixture and reload to recover green. Reload and resize should
preserve the positive result. Automated tests separately ensure active missing
inputs, malformed declarations, depth writes and texture feedback still reject.
