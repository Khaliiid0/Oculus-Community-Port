#version 120
/* DRAWBUFFERS:04 */
const int colortex0Format=R11F_G11F_B10F;
const int colortex4Format=RGBA16F;
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform float far;
void main(){gl_FragData[0]=texture2D(colortex0,texcoord);gl_FragData[1]=vec4(far,0.25,0.75,1.0);}
