#version 120
/* DRAWBUFFERS:4 */
#include "/settings.glsl"
uniform sampler2D colortex4;
uniform float wetness;
uniform float wetReference;
uniform float rainStrength;
varying float vertexWetness;
void main(){
    vec4 old=texelFetch(colortex4,ivec2(gl_FragCoord.xy),0);
    float observed=wetness;
#if NEGATIVE_MODE == 1
    observed=rainStrength;
#endif
    bool bad=old.a!=.375||abs(old.r-observed)>.00002
        ||abs(old.g-wetReference)>.00002||abs(old.b-vertexWetness)>.00002
        ||abs(wetness-wetReference)>.00002;
    gl_FragColor=vec4(old.r,old.g,bad?1.:0.,.375);
}
