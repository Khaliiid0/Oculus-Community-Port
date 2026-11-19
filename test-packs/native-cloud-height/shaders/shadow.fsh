#version 330 compatibility
/* DRAWBUFFERS:0 */
uniform float cloudHeight;
in float cloudFromVertex;
void main(){
    bool bad=abs(cloudFromVertex-cloudHeight)>.002;
    gl_FragColor=vec4(bad?vec3(1,0,1):vec3(cloudHeight/512.,.25,.75),1);
}
