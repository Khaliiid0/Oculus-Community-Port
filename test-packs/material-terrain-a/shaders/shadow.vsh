#version 120
attribute vec3 mc_Entity;
varying vec2 uv;
varying vec3 material;
void main() {
    uv = gl_MultiTexCoord0.xy;
    material = mc_Entity;
    gl_Position = ftransform();
}
