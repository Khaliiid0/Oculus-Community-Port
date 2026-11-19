#version 120
uniform float heightProbe;
uniform float isDesert;
varying vec2 vertexValues;
void main(){vertexValues=vec2(heightProbe,isDesert);gl_Position=ftransform();}
