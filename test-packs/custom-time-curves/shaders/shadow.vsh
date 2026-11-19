#version 120
uniform float declaredFade;
uniform float declaredBrightness;
varying vec2 vertexCurves;
void main(){vertexCurves=vec2(declaredFade,declaredBrightness);gl_Position=ftransform();}
