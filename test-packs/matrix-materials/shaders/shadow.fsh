#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
uniform sampler2D texture;
varying vec2 uv;
varying float matrixError;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    gl_FragColor = matrixError > 0.005 ? vec4(1,0,1,1) : vec4(0.125,0.375,0.625,1);
}
