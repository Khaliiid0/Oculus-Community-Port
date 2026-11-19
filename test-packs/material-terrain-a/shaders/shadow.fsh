#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
varying vec2 uv;
varying vec3 material;
uniform sampler2D texture;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    bool valid = material.x == -1.0 || (material.x >= 101.0 && material.x <= 104.0);
    valid = valid && material.z == 0.0;
    gl_FragColor = valid ? vec4(0.125, 0.375, 0.625, 1) : vec4(1, 0, 1, 1);
}
