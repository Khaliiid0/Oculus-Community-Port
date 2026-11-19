#version 120
uniform vec3 relativeEyePosition;
varying vec2 uv;
varying vec3 eyeOffset;
void main() {
    uv = gl_MultiTexCoord0.xy;
    eyeOffset = relativeEyePosition;
    gl_Position = ftransform();
}
