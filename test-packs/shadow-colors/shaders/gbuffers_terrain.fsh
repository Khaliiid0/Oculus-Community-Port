#version 120
/* DRAWBUFFERS:0 */
uniform sampler2D texture;
varying vec2 uv;
#include "markers.glsl"
void main(){
    if(texture2D(texture,uv).a<.1)discard;
    gl_FragColor=pairResult();
}
