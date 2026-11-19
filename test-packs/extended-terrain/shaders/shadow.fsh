#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
varying vec2 uv;
varying vec3 normal;
varying vec4 tangent;
uniform sampler2D texture;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    bool valid = abs(length(normal) - 1.0) < 0.02 && abs(length(tangent.xyz) - 1.0) < 0.02 && abs(dot(normal, tangent.xyz)) < 0.03;
    gl_FragColor = valid ? vec4(0.125, 0.375, 0.625, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
