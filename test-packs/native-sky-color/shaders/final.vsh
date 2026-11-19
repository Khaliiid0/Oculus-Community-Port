#version 120
uniform vec3 skyColor;
varying vec3 skyFromPostVertex;
void main(){skyFromPostVertex=skyColor;gl_Position=ftransform();}
