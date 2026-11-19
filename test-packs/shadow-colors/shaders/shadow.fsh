#version 120
/* DRAWBUFFERS:10 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
uniform sampler2D texture;
uniform int frameCounter;
varying vec2 uv;
vec4 marker(int value) {
    return vec4(float(value%256),float((value/256)%256),float(value/65536),255.0)/255.0;
}
void main(){
    if(texture2D(texture,uv).a<0.1)discard;
    gl_FragData[0]=marker((frameCounter+7919)%720720);
    gl_FragData[1]=marker(frameCounter%720720);
}
