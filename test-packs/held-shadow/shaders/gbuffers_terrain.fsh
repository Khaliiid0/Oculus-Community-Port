#version 120
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
void main(){gl_FragColor=vec4(float(heldBlockLightValue)/15.0,.25,float(heldBlockLightValue2)/15.0,1);}
