#version 120
uniform float wetness;
varying float postVertexWetness;
void main(){postVertexWetness=wetness;gl_Position=ftransform();}
