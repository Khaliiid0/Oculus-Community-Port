#version 120
/* DRAWBUFFERS:04 */
const int colortex4Format=RGBA16F;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
void main() {
    vec4 albedo=texture2D(texture,uv)*tint;
    gl_FragData[0]=vec4(albedo.rgb*texture2D(lightmap,lm).rgb,albedo.a);
    // A clear marker cannot masquerade as this frame's deferred producer.
    gl_FragData[1]=vec4(0.0);
}
