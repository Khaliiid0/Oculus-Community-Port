#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
#include "camera.glsl"
varying vec3 previousFromVertex;
void main(){
    bool bad=any(greaterThan(abs(previousFromVertex-previousCameraPosition),vec3(.0001)));
    gl_FragColor=vec4(bad?vec3(1,0,1):marker(previousCameraPosition),1);
}
