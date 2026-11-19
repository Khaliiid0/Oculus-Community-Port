#version 120
#include "verify.glsl"
void main(){
    bool bad=texture2D(colortex7,texcoord).r>.5||badNative();
    gl_FragColor=bad?vec4(1,0,1,1):hasShadow()?vec4(0,1,0,1):vec4(1,.5,0,1);
}
