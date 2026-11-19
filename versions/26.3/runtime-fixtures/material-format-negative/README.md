# Deliberately failing resource-format control

See `../material-format/README.md`. `shaders/mode.glsl` deliberately forces a
terrain/shadow marker mismatch. The upper-left panel must be magenta, not the
positive format's color, on both indirect and separate terrain backends.
This is an authored local test input, not a distributable shader pack.
