#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform int worldTime;
varying vec2 texcoord;
const float expectedOffset=0.0;
void main(){
float t=float(worldTime);
float fade=1.0;
if(t>=12330.0&&t<12560.0) fade=1.0-(t-12330.0)/230.0;
else if(t>=12560.0&&t<13010.0) fade=0.0;
else if(t>=13010.0&&t<13230.0) fade=(t-13010.0)/220.0;
else if(t>=22770.0&&t<22990.0) fade=1.0-(t-22770.0)/220.0;
else if(t>=22990.0&&t<23440.0) fade=0.0;
else if(t>=23440.0&&t<23670.0) fade=(t-23440.0)/230.0;
float brightness=max(sin(t*(6.28318530718/24000.0)),0.0);
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=texcoord.x>=.5?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
bool marked=marker.a>.99&&(abs(marker.b-.25)<.01||abs(marker.b-.75)<.01);
bool bad=marker.b>.5||any(greaterThan(abs(marker.rg-vec2(fade+expectedOffset,brightness)),vec2(.006)));
gl_FragColor=!marked?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
