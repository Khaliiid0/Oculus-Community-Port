#version 120
/* DRAWBUFFERS:01 */
const int shadowMapResolution=512;
const float shadowDistance=64.0;
#include "inputs.glsl"
varying vec4 vertexInputs;
void main(){
    bool bad=any(greaterThan(abs(vertexInputs-inputs()),vec4(.0001)))
        ||abs(maxBlindnessDarkness-max(blindness,darknessFactor))>.0001;
    gl_FragData[0]=bad?vec4(1,0,1,1):vec4(inputs().rgb,1);
    gl_FragData[1]=vec4(maxBlindnessDarkness,0,0,1);
}
