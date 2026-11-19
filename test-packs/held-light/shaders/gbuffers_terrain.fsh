#version 120
/* DRAWBUFFERS:0 */
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
varying vec2 heldVertex;
void main(){
vec2 values=vec2(heldBlockLightValue,heldBlockLightValue2);
gl_FragColor=any(greaterThan(abs(values-heldVertex),vec2(.01)))?vec4(1,0,1,1):vec4(values.x/15.0,0.25,values.y/15.0,1);
}
