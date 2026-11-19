#version 330 compatibility
/* DRAWBUFFERS:04 */
#include "/check.glsl"
void main(){gl_FragData[0]=materialMarker();gl_FragData[1]=materialMarker();}
