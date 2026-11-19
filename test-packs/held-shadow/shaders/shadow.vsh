#version 120
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
varying vec2 heldVertex;
void main(){heldVertex=vec2(heldBlockLightValue,heldBlockLightValue2);gl_Position=ftransform();}
