#version 120
const float centerDepthHalflife=10.0;
uniform sampler2D colortex0;
uniform sampler2D colortex7;
uniform float centerDepthSmooth;
varying vec2 uv;
varying float vertexCenter;
void main(){
    vec4 oracle=texelFetch(colortex7,ivec2(0),0);
    bool correct=oracle.b>0.5&&abs(oracle.r-centerDepthSmooth)<0.0000003&&abs(vertexCenter-centerDepthSmooth)<0.0000002;
    vec4 marker=correct?vec4(0,0.75,1,1):vec4(1,0,1,1);
    gl_FragColor=uv.x<0.22&&uv.y>0.75?marker:texture2D(colortex0,uv);
}
