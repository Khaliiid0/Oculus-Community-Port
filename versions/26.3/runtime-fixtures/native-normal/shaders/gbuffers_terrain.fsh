#version 120
/* DRAWBUFFERS:07 */
const int colortex7Format = RGBA16F;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
void main() {
    vec4 albedo = texture2D(texture, uv) * tint;
    if (albedo.a < 0.1) discard;
    // Normal-view control: no diagnostic tint or split-screen overlay.
    gl_FragData[0] = vec4(albedo.rgb * texture2D(lightmap, lm).rgb, albedo.a);
    gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);
}
