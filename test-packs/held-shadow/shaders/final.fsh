#version 120
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
const int expectedMain=0;
const int expectedOff=0;
void main(){
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=texelFetch(shadowcolor0,clamp(ivec2(texcoord*vec2(size)),ivec2(0),size-ivec2(1)),0);
bool bad=any(notEqual(ivec2(round(marker.rg*255.0)),ivec2(expectedMain,expectedOff)))||marker.b>.01;
gl_FragColor=marker.a<.99?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
