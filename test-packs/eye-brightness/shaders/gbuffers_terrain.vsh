#version 120
uniform ivec2 eyeBrightness;
varying vec2 vertexEyeBrightness;
void main(){vertexEyeBrightness=vec2(eyeBrightness);gl_Position=ftransform();}
