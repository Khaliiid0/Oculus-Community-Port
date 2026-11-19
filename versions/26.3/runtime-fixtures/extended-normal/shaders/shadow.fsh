#version 120
varying float shadowMeshBad;
/* DRAWBUFFERS:0 */
varying vec2 uv;
uniform sampler2D texture;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    gl_FragColor = shadowMeshBad > .5 ? vec4(1,0,1,1) : vec4(0.125, 0.375, 0.625, 1.0);
}
