#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform ivec2 eyeBrightness;
varying vec2 vertexEyeBrightness;
void main(){bool mismatch=any(greaterThan(abs(vec2(eyeBrightness)-vertexEyeBrightness),vec2(.01)));
gl_FragColor=vec4(vec2(eyeBrightness)/240.0,mismatch?.75:.25,1);}
