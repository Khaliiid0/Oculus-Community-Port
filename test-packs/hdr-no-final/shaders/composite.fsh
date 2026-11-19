#version 120
/*
const int colortex0Format = RGBA16F;
*/
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
void main() { gl_FragColor = texture2D(colortex0, texcoord); }
