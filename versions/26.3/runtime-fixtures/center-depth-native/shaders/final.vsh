#version 120
uniform float centerDepthSmooth;
varying vec2 uv;
varying float vertexCenter;
void main(){uv=gl_MultiTexCoord0.xy;vertexCenter=centerDepthSmooth;gl_Position=ftransform();}
