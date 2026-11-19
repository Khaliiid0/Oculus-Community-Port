#version 330 compatibility
/* DRAWBUFFERS:04 */
#include "fragment.glsl"
void main(){gl_FragData[0]=payload();gl_FragData[1]=payload();}
