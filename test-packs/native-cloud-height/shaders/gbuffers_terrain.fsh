#version 330 compatibility
/* DRAWBUFFERS:04 */
uniform float cloudHeight;
in float cloudFromVertex;
void main(){
    bool bad=abs(cloudFromVertex-cloudHeight)>.002;
    vec4 value=vec4(bad?vec3(1,0,1):vec3(cloudHeight/512.,.25,.75),1);
    gl_FragData[0]=value;
    gl_FragData[1]=value;
}
