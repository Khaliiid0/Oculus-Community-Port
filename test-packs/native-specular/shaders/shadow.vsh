#version 330 compatibility
out vec2 materialUv;
void main(){materialUv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}
