# Intentional GPU-compile failure fixture

Development test only; never distribute as a working shader pack. Preparation succeeds, the deferred stage allocates a uniform ring, and the final stage references an undefined symbol. The GPU must reject it, close any partially allocated programs, and preserve the previous working shader-only generation.
