#version 120
uniform int worldTime;
uniform int worldDay;
varying vec2 uv;
varying float vertexTime;
varying float vertexDay;
void main() {
    uv = gl_MultiTexCoord0.xy;
    vertexTime = float(worldTime);
    vertexDay = float(worldDay);
    gl_Position = ftransform();
}
