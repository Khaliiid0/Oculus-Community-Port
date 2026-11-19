#version 120
uniform float nightVision;
varying float vertexNightVision;
void main(){vertexNightVision=nightVision;gl_Position=ftransform();}
