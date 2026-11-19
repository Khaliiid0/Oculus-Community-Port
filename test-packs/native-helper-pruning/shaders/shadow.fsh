#version 120
/* DRAWBUFFERS:0 */
#include "helpers.glsl"
varying vec3 liveFromVertex;
void main(){
    bool bad=any(greaterThan(abs(liveFromVertex-activeMarker()),vec3(.0001)));
    gl_FragColor=vec4(bad?vec3(1,0,1):activeMarker(),1);
}
