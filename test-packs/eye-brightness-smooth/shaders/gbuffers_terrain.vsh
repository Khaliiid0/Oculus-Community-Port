#version 120
uniform ivec2 eyeBrightnessSmooth;
varying vec2 vertexEyeBrightness;
void main(){vertexEyeBrightness=vec2(eyeBrightnessSmooth);gl_Position=ftransform();}
