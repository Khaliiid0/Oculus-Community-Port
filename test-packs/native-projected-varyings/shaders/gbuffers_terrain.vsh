#version 120
uniform mat4 gbufferProjection;
uniform mat4 gbufferModelView;
varying vec4 namedClip;
varying vec4 builtinClip;
varying vec3 viewPosition;
void main() {
    viewPosition = (gbufferModelView * gl_Vertex).xyz;
    namedClip = gbufferProjection * vec4(viewPosition, 1.0);
    builtinClip = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
    gl_Position = ftransform();
}
