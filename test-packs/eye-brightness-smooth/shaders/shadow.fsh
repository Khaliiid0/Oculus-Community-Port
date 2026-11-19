#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform ivec2 eyeBrightnessSmooth;
varying vec2 vertexEyeBrightness;
void main(){bool mismatch=any(greaterThan(abs(vec2(eyeBrightnessSmooth)-vertexEyeBrightness),vec2(.01)));
gl_FragColor=vec4(vec2(eyeBrightnessSmooth)/240.0,mismatch?.75:.25,1);}
