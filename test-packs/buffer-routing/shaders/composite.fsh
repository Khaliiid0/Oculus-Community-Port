#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
void main() { gl_FragColor = texture2D(colortex0, texcoord); }
