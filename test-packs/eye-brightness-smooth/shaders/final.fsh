#version 120
const float eyeBrightnessHalflife=400.0;
const bool forceMismatch=false;
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform ivec2 eyeBrightness;
uniform ivec2 eyeBrightnessSmooth;
varying vec2 texcoord;
void main(){
// Top four bars, from top downward: raw block, smooth block, raw sky, smooth sky.
if(texcoord.y>.8){
int row=int((1.0-texcoord.y)*20.0);
float value=row==0?float(eyeBrightness.x):row==1?float(eyeBrightnessSmooth.x):row==2?float(eyeBrightness.y):float(eyeBrightnessSmooth.y);
gl_FragColor=texcoord.x<value/240.0?vec4(1,1,1,1):vec4(.08,.08,.08,1);return;
}
bool shadowSide=texcoord.x>=.5;
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y/.8);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=shadowSide?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
bool marked=marker.a>.99&&(abs(marker.b-.25)<.01||abs(marker.b-.75)<.01);
ivec2 observed=ivec2(round(marker.rg*240.0));
bool bad=forceMismatch||marker.b>.5||any(greaterThan(abs(observed-eyeBrightnessSmooth),ivec2(1)));
bool changing=any(greaterThan(abs(eyeBrightness-eyeBrightnessSmooth),ivec2(16)));
gl_FragColor=!marked?vec4(0,0,0,1):(bad?vec4(1,0,1,1):(changing?vec4(0,1,1,1):vec4(0,1,0,1)));
}
