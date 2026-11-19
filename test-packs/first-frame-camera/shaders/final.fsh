#version 120
uniform sampler2D colortex7;
varying vec2 texcoord;
void main(){vec4 marker=texture2D(colortex7,texcoord);
gl_FragColor=marker.a<.5?vec4(0,0,0,1):(marker.r>.5?vec4(1,0,1,1):vec4(0,1,0,1));}
