#version 120
/* DRAWBUFFERS:0 */
// Internal bridge fixture: terrain must not overwrite the history attachment.
void main() { gl_FragColor = vec4(1.0); }
