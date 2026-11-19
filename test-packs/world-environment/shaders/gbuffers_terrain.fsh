#version 120
uniform int moonPhase;
uniform float rainStrength;
varying vec2 environmentVertex;
void main(){vec2 values=vec2(float(moonPhase),rainStrength);
gl_FragColor=vec4(values/vec2(7.0,1.0),any(greaterThan(abs(values-environmentVertex),vec2(.001)))?1.0:0.0,1);}
