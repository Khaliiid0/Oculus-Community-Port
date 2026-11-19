#version 330 compatibility
/* DRAWBUFFERS:01 */
#include "fragment.glsl"
void main(){gl_FragData[0]=markerA();gl_FragData[1]=markerB();}
