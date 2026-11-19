#version 330 compatibility
/* DRAWBUFFERS:046 */
#include "fragment.glsl"
void main(){gl_FragData[0]=markerA();gl_FragData[1]=markerA();gl_FragData[2]=markerB();}
