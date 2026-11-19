#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform sampler2D texture;
uniform int frameCounter;
varying vec2 uv;
void main(){if(texture2D(texture,uv).a<0.1)discard;
int value=frameCounter;
gl_FragColor=vec4(float(value%256),float((value/256)%256),float(value/65536),255.0)/255.0;}
