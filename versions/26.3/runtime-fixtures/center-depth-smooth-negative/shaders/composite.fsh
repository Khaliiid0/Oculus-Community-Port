#version 120
/* DRAWBUFFERS:7 */
const int colortex7Format=RGBA32F;
const bool colortex7Clear=false;
// Deliberately bypass smoothing; the independent one-second oracle must fail.
const float centerDepthHalflife=0.0;
uniform sampler2D colortex7;
uniform sampler2D depthtex2;
uniform float centerDepthSmooth;
uniform float frameTime;
uniform int frameCounter;
void main(){
    vec4 old=texelFetch(colortex7,ivec2(0),0);
    bool previous=old.a==0.375&&abs(old.g-float(frameCounter-1))<0.1;
    float instant=texelFetch(depthtex2,textureSize(depthtex2,0)/2,0).r;
    float blend=1.0-exp(-0.69314718056*frameTime);
    float expected=previous?mix(old.r,instant,blend):instant;
    bool correct=abs(expected-centerDepthSmooth)<0.0000005;
    // Latch any error until reload, so a transient failure cannot disappear.
    bool healthy=correct&&(!previous||old.b>0.5);
    gl_FragColor=vec4(centerDepthSmooth,float(frameCounter),healthy?1.0:0.0,0.375);
}
