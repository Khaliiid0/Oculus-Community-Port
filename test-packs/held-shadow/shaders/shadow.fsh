#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
varying vec2 heldVertex;
void main(){vec2 values=vec2(heldBlockLightValue,heldBlockLightValue2);
gl_FragColor=vec4(values/255.0,any(greaterThan(abs(values-heldVertex),vec2(.01)))?1.0:0.0,1);}
