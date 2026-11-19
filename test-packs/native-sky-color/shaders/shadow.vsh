#version 330 compatibility
uniform vec3 skyColor;
out vec3 skyFromVertex;
void main(){skyFromVertex=skyColor;gl_Position=ftransform();}
