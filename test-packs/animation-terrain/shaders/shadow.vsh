#version 120
uniform int frameCounter;
uniform float frameTime, frameTimeCounter;
varying vec2 uv;
varying vec3 animation;
void main() {
    uv = gl_MultiTexCoord0.xy;
    animation = vec3(float(frameCounter), frameTime, frameTimeCounter);
    gl_Position = ftransform();
}
