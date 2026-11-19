# Actual terrain shader probe

Development-only Minecraft 26.2 test, excluded from production JARs. The paired terrain
vertex/fragment programs must actually compile and execute. Green-tinted terrain is deliberate
pack logic, not a built-in mod overlay. Its auxiliary output is a known HDR marker, checked by
the final shader. Magenta terrain means the generic internal gbuffer replaced the real pack.

The paired shadow shaders also write a fixed shadowcolor0 marker, checked by the final pass.
Logs must say PACK-NATIVE at compilation and terrain/shadow submission. Check terrain geometry,
atlas textures, lighting, transparency, resize and resource reload. This fixture does not validate
BSL/Complementary, vertex normals/materials, water-specific programs, or pack-accurate shadows.
