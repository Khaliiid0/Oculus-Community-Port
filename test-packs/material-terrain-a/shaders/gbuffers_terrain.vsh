#version 120
attribute vec4 mc_Entity;
varying vec2 uv;
varying vec4 material;
void main() {
    uv = gl_MultiTexCoord0.xy;
    material = mc_Entity;
    gl_Position = ftransform();
}
