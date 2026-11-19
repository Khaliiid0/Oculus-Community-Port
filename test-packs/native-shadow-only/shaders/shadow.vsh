#version 120
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
varying vec2 uv;
void main() {
    uv = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
