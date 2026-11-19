#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform int moonPhase;
uniform float rainStrength;
varying vec2 environmentVertex;
void main(){vec2 values=vec2(float(moonPhase),rainStrength);
gl_FragColor=vec4(values/vec2(7.0,1.0),any(greaterThan(abs(values-environmentVertex),vec2(.001)))?1.0:0.0,1);}
