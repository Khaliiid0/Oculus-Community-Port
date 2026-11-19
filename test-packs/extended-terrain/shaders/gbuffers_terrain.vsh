#version 120
attribute vec4 at_tangent;
attribute vec4 mc_midTexCoord;
varying vec2 uv;
varying vec3 normal;
varying vec4 tangent;
varying vec2 midUv;
void main() {
    uv = gl_MultiTexCoord0.xy;
    normal = gl_Normal;
    tangent = at_tangent;
    midUv = mc_midTexCoord.xy;
    gl_Position = ftransform();
}
