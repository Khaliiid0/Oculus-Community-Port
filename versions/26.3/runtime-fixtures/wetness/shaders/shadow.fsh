#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.;
const int shadowcolor0Format=RGBA8;
uniform float wetness;
varying float vertexWetness;
void main(){gl_FragColor=vec4(wetness,vertexWetness,.125,.375);}
