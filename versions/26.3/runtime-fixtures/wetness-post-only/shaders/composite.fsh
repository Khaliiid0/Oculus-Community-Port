#version 120
/* DRAWBUFFERS:04 */
#include "/settings.glsl"
const int colortex4Format=RGBA32F;
uniform sampler2D colortex0;
uniform float wetness;
uniform float wetReference;
varying float vertexWetness;
void main(){
    gl_FragData[0]=texelFetch(colortex0,ivec2(gl_FragCoord.xy),0);
    gl_FragData[1]=vec4(wetness,wetReference,vertexWetness,.375);
}
