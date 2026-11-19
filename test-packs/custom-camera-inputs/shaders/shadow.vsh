#version 120
uniform float cameraProbe;
uniform float eyeAltitude;
varying vec2 vertexValues;
void main(){vertexValues=vec2(cameraProbe,clamp(eyeAltitude/512.0,0.0,1.0));gl_Position=ftransform();}
