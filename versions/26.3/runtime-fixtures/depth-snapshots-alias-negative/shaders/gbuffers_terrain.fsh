#version 120
/* DRAWBUFFERS:04 */
const int colortex0Format=RGBA16F;
const int colortex4Format=RGBA32F;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
void main(){vec4 a=texture2D(texture,uv)*tint;gl_FragData[0]=vec4(a.rgb*texture2D(lightmap,lm).rgb,a.a);gl_FragData[1]=vec4(0.0);}
