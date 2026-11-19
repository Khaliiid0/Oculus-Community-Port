#version 120
/* DRAWBUFFERS:04 */
#include "camera.glsl"
varying vec3 previousFromVertex;
void main(){
    bool bad=any(greaterThan(abs(previousFromVertex-previousCameraPosition),vec3(.0001)));
    gl_FragData[0]=vec4(marker(previousCameraPosition),1);
    gl_FragData[1]=vec4(bad?vec3(1,0,1):marker(previousCameraPosition),1);
}
