#version 120
/* DRAWBUFFERS:0 */
const float eyeBrightnessHalflife=400.0;
uniform ivec2 eyeBrightnessSmooth;
void main(){gl_FragColor=vec4(vec2(eyeBrightnessSmooth)/240.0,.25,1);}
