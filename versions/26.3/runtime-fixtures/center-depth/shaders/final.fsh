#version 120
const float centerDepthHalflife=0.0;
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float centerDepthSmooth;
varying vec2 uv;
varying float vertexCenter;
void main(){
    float expected=texelFetch(depthtex0,textureSize(depthtex0,0)/2,0).r;
    bool correct=abs(centerDepthSmooth-expected)<0.0000003&&abs(vertexCenter-centerDepthSmooth)<0.0000002;
    vec4 marker=!correct?vec4(1,0,1,1):expected<0.9999?vec4(0,0.75,1,1):vec4(1);
    gl_FragColor=uv.x<0.22&&uv.y>0.75?marker:texture2D(colortex0,uv);
}
