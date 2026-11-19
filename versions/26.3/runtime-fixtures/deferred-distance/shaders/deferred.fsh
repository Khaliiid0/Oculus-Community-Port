#version 120
/* DRAWBUFFERS:4 */
const int colortex4Format=RGBA16F;
uniform float far;
void main(){gl_FragData[0]=vec4(far,0.25,0.75,1.0);}
