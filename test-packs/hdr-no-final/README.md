# HDR presentation without final

26.2 development-only fixture. Copies the scene through a RGBA16F colortex0 target with
no final program. Exercises the explicit pixel-exact format-converting presentation draw
instead of an invalid cross-format texture copy. Expected appearance: unchanged scene.
