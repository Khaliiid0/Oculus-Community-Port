#version 120
#include "inputs.glsl"
varying vec4 vertexInputs;
void main(){vertexInputs=inputs();gl_Position=ftransform();}
