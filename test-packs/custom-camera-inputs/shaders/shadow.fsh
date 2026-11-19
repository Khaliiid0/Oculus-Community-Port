#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform float cameraProbe;
uniform float eyeAltitude;
varying vec2 vertexValues;
void main(){vec2 values=vec2(cameraProbe,clamp(eyeAltitude/512.0,0.0,1.0));
bool bad=any(greaterThan(abs(values-vertexValues),vec2(.0001)));
gl_FragColor=vec4(values,bad?.75:.25,.375);}
