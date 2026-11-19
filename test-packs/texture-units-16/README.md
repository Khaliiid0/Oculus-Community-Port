# Sixteen active texture binding diagnostic

Two eight-output passes fill colortex0 through colortex15 with distinct red
values. All values alternate by 0.5 each frame using the shared animation clock.
The final fragment reads all sixteen independently and compares each value; its
vertex stage also reads colortex15. Green is agreement; magenta is any mismatch.
This checks high units and cross-stage binding, including stale-frame data.

The R8 producer images keep fullscreen scratch memory within the existing budget.
Set expectedOffset to 0.05 in a copied profile for a deliberate failure control,
reload, then restore 0.0. Flat colors are diagnostic, not shader-pack appearance.
