#version 330 compatibility
uniform float darknessLightFactor,darknessFactor;
out vec2 darknessFromVertex;
void main(){darknessFromVertex=vec2(darknessLightFactor,darknessFactor);gl_Position=ftransform();}
