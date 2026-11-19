#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
#include "markers.glsl"
void main(){
    // Left: post consumer. Right: native terrain consumer (sky stays vanilla).
    gl_FragColor=texcoord.x<.5?pairResult():texture2D(colortex0,texcoord);
}
