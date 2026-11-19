#version 120
/* DRAWBUFFERS:04 */
#include "/check.glsl"
uniform sampler2D texture;
uniform sampler2D lightmap;
varying vec2 lm;
varying vec4 tint;
void main(){gl_FragData[0]=texture2D(texture,uv)*tint*vec4(texture2D(lightmap,lm).rgb,1.0);gl_FragData[1]=contextMarker();}
