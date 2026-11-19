#version 120
/* DRAWBUFFERS:0 */
uniform sampler2D colortex0;
uniform ivec2 eyeBrightnessSmooth;
varying vec2 texcoord;
void main(){
vec4 previous=texture2D(colortex0,texcoord);
bool mismatch=any(greaterThan(abs(ivec2(round(previous.rg*240.0))-eyeBrightnessSmooth),ivec2(1)));
gl_FragColor=vec4(previous.rg,mismatch?.75:previous.b,1);
}
