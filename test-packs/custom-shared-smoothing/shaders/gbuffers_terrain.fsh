#version 120
/* DRAWBUFFERS:0 */
uniform float heightProbe;
uniform float isDesert;
varying vec2 vertexValues;
void main(){vec2 values=vec2(heightProbe,isDesert);
bool bad=any(greaterThan(abs(values-vertexValues),vec2(.0001)));
gl_FragColor=vec4(values,bad?.75:.25,.375);}
