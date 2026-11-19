#version 120
/* DRAWBUFFERS:7 */
#include "verify.glsl"
void main(){gl_FragColor=vec4(max(texture2D(colortex7,texcoord).r,badNative()?1.0:0.0),0,0,1);}
