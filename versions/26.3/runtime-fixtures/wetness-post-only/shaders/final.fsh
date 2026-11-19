#version 120
#include "/settings.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform float wetness;
uniform float wetReference;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
varying float vertexWetness;
void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    if(pixel.x>=int(viewWidth*.22)||pixel.y<=int(viewHeight*.75)){
        gl_FragColor=texelFetch(colortex0,pixel,0);return;
    }
    vec4 marker=texelFetch(colortex4,pixel,0);
    bool bad=marker.a!=.375||marker.b>.5||abs(marker.r-wetness)>.00002
        ||abs(marker.g-wetReference)>.00002||abs(vertexWetness-wetness)>.00002
        ||abs(wetness-wetReference)>.00002;
    if(pixel.y>int(viewHeight*.95)){
        float value=pixel.y>int(viewHeight*.975)?rainStrength:wetness;
        gl_FragColor=float(pixel.x)/viewWidth<.22*value?vec4(.3,.6,1,1):vec4(.05,.05,.05,1);return;
    }
    bool changing=abs(rainStrength-wetness)>.02;
    gl_FragColor=bad?vec4(1,0,1,1):changing?vec4(0,.75,1,1):vec4(0,.75,0,1);
}
