#version 120
const int shadowMapResolution=256;
const float shadowDistance=128.0;
uniform float heightProbe;
uniform float isDesert;
varying vec2 vertexValues;
void main(){vertexValues=vec2(heightProbe,isDesert);gl_Position=ftransform();}
