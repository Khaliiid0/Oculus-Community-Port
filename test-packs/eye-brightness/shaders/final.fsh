#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform ivec2 eyeBrightness;
varying vec2 texcoord;
const ivec2 expectedBrightness=ivec2(0,240);
void main(){
bool shadowSide=texcoord.x>=.5;
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=shadowSide?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
bool marked=marker.a>.99&&(abs(marker.b-.25)<.01||abs(marker.b-.75)<.01);
ivec2 observed=ivec2(round(marker.rg*240.0));
bool bad=marker.b>.5||any(notEqual(observed,eyeBrightness))||any(notEqual(eyeBrightness,expectedBrightness));
gl_FragColor=!marked?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
