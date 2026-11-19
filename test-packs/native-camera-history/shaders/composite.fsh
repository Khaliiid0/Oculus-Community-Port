#version 120
/* DRAWBUFFERS:76 */
const bool colortex6Clear=false;
const bool colortex7Clear=false;
const vec4 colortex6ClearColor=vec4(0);
const vec4 colortex7ClearColor=vec4(0);
#include "verify.glsl"
uniform sampler2D colortex6;
void main(){
    vec4 lastFrame=texture2D(colortex6,texcoord);
    vec3 previous=marker(previousCameraPosition);
#ifdef NEGATIVE_HISTORY_TEST
    previous=marker(cameraPosition);
#endif
    // First allocation/reload/resize has no retained GPU marker: skip that one
    // comparison, but still compare native/post bindings. CPU tests cover first
    // frame previous=current. Once seeded, no CPU history drives this oracle.
    bool bad=badNative()||(lastFrame.a>.999&&differs(lastFrame.rgb,previous));
    gl_FragData[0]=vec4(max(texture2D(colortex7,texcoord).r,bad?1.0:0.0),0,0,1);
    gl_FragData[1]=vec4(marker(cameraPosition),1);
}
