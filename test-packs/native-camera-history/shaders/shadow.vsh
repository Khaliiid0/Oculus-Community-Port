#version 120
#include "camera.glsl"
varying vec3 previousFromVertex;
void main(){previousFromVertex=previousCameraPosition;gl_Position=ftransform();}
