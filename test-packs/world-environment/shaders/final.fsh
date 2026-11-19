#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform int moonPhase;
uniform float rainStrength;
varying vec2 texcoord;
const int expectedMoon=0;
const float expectedRain=0.0;
void main(){
bool shadowSide=texcoord.x>=.5;
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=shadowSide?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
bool bad=int(round(marker.r*7.0))!=expectedMoon||abs(marker.g-expectedRain)>.01||marker.b>.01;
bad=bad||moonPhase!=expectedMoon||abs(rainStrength-expectedRain)>.01;
bad=bad||int(round(marker.r*7.0))!=moonPhase||abs(marker.g-rainStrength)>.01;
gl_FragColor=marker.a<.99?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
