#version 120
const bool forceMismatch=false;
uniform sampler2D colortex0;
uniform ivec2 eyeBrightness;
uniform ivec2 eyeBrightnessSmooth;
varying vec2 texcoord;
void main(){
if(texcoord.y>.8){
int row=int((1.0-texcoord.y)*20.0);
float value=row==0?float(eyeBrightness.x):row==1?float(eyeBrightnessSmooth.x):row==2?float(eyeBrightness.y):float(eyeBrightnessSmooth.y);
gl_FragColor=texcoord.x<value/240.0?vec4(1,1,1,1):vec4(.08,.08,.08,1);return;
}
vec4 previous=texture2D(colortex0,texcoord);
bool bad=forceMismatch||previous.b>.5||any(greaterThan(abs(ivec2(round(previous.rg*240.0))-eyeBrightnessSmooth),ivec2(1)));
bool changing=any(greaterThan(abs(eyeBrightness-eyeBrightnessSmooth),ivec2(16)));
gl_FragColor=bad?vec4(1,0,1,1):(changing?vec4(0,1,1,1):vec4(0,1,0,1));
}
