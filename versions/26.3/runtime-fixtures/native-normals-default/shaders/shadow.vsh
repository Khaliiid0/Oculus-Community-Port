#version 120
varying vec2 materialUv;
void main(){materialUv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;gl_Position=ftransform();}
