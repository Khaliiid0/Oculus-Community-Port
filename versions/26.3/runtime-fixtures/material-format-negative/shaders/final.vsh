#version 120
#include "/context.glsl"
varying float postVertexFormat;
void main(){postVertexFormat=float(RESOURCE_FORMAT_CODE);gl_Position=ftransform();}
