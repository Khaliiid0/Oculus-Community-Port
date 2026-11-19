#version 120
uniform float cloudHeight;
varying float cloudFromVertex;
void main(){gl_FragColor=abs(cloudFromVertex-cloudHeight)>.01?vec4(1,0,1,1):vec4(0,1,0,1);}
