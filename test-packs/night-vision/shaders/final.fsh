#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform float nightVision;
varying vec2 texcoord;
const float expectedNightVision=0.0;
const bool compareExpected=true;
void main(){
bool shadowSide=texcoord.x>=.5;
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=shadowSide?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
bool bad=marker.g>.01||abs(marker.r-nightVision)>.01||nightVision<0.0||nightVision>1.0;
bad=bad||(compareExpected&&abs(nightVision-expectedNightVision)>.01);
gl_FragColor=marker.a<.99||abs(marker.b-.25)>.01?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
