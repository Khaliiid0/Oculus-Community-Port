#version 120
/* DRAWBUFFERS:04 */
#include "helpers.glsl"
varying vec3 liveFromVertex;
void main(){
    bool bad=any(greaterThan(abs(liveFromVertex-activeMarker()),vec3(.0001)));
    vec4 payload=vec4(bad?vec3(1,0,1):activeMarker(),1);
    gl_FragData[0]=payload;
    gl_FragData[1]=payload;
}
