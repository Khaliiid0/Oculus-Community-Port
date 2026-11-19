#version 330 compatibility
uniform float cloudHeight;
out float cloudFromVertex;
void main(){cloudFromVertex=cloudHeight;gl_Position=ftransform();}
