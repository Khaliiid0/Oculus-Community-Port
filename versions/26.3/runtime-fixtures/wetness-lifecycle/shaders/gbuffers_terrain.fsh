#version 120
/* DRAWBUFFERS:04 */
const int colortex4Format=RGBA32F;
uniform float wetness;
uniform sampler2D texture;
uniform sampler2D lightmap;
varying float vertexWetness;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
float puddles(float wetness){return wetness;}
void main(){
    gl_FragData[0]=texture2D(texture,uv)*tint*vec4(texture2D(lightmap,lm).rgb,1.0);
    gl_FragData[1]=vec4(puddles(wetness),vertexWetness,.125,.375);
}
