#version 120
uniform float framemod8, framemod2, timeAngle;
varying vec2 uv;
varying vec3 customClock;
void main() {
    uv = gl_MultiTexCoord0.xy;
    customClock = vec3(framemod8, framemod2, timeAngle);
    gl_Position = ftransform();
}
