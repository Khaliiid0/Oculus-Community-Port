#version 120
/* DRAWBUFFERS:0 */
uniform float declaredFade;
uniform float declaredBrightness;
varying vec2 vertexCurves;
void main(){vec2 curves=vec2(declaredFade,declaredBrightness);
bool bad=any(greaterThan(abs(curves-vertexCurves),vec2(.0001)));
gl_FragColor=vec4(curves,bad?.75:.25,1);}
