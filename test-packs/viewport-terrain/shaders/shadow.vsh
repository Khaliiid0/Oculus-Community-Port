#version 120
uniform float viewWidth, viewHeight, aspectRatio;
varying vec2 uv;
varying vec3 viewport;
void main() {
    uv = gl_MultiTexCoord0.xy;
    viewport = vec3(viewWidth, viewHeight, aspectRatio);
    gl_Position = ftransform();
}
