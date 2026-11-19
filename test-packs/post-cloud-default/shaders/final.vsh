#version 120
uniform float cloudHeight=192.33;
varying float cloudFromVertex;
void main(){cloudFromVertex=cloudHeight;gl_Position=ftransform();}
