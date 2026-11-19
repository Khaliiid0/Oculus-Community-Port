#version 120
uniform int moonPhase;
uniform float rainStrength;
varying vec2 environmentVertex;
void main(){environmentVertex=vec2(float(moonPhase),rainStrength);gl_Position=ftransform();}
