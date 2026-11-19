#version 120
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;
uniform vec3 cameraPosition;
varying vec3 worldPosition;
void main() {
    worldPosition = gl_Vertex.xyz + cameraPosition;
    gl_Position = shadowProjection * shadowModelView * gl_Vertex;
}
