#version 120
varying vec2 texcoord;
varying vec4 fogFromPostVertex;
void main(){
    texcoord=gl_MultiTexCoord0.xy;
    fogFromPostVertex=vec4(gl_Fog.start,gl_Fog.end,gl_Fog.scale,gl_Fog.color.a);
    gl_Position=ftransform();
}
