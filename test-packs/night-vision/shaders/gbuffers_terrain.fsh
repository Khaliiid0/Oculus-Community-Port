#version 120
/* DRAWBUFFERS:0 */
uniform float nightVision;
varying float vertexNightVision;
void main(){gl_FragColor=vec4(nightVision,abs(nightVision-vertexNightVision)>.001?1.0:0.0,.25,1);}
