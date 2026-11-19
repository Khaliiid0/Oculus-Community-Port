#version 120
#include "/context.glsl"
varying vec2 uv;
varying float vertexFormat;
void main(){uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;vertexFormat=float(RESOURCE_FORMAT_CODE);gl_Position=ftransform();}
