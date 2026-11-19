#version 120
/* DRAWBUFFERS:0 */
#include "inputs.glsl"
varying vec4 vertexInputs;
void main(){
    bool bad=any(greaterThan(abs(vertexInputs-inputs()),vec4(.0001)))
        ||abs(maxBlindnessDarkness-max(blindness,darknessFactor))>.0001;
    gl_FragColor=bad?vec4(1,0,1,.875):vec4(inputs().rgb,.875);
}
