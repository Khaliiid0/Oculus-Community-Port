#version 120
attribute vec4 at_tangent;
varying vec2 uv;
varying vec3 normal;
varying vec4 tangent;
void main() {
    uv = gl_MultiTexCoord0.xy;
    normal = gl_Normal;
    tangent = at_tangent;
    gl_Position = ftransform();
}
