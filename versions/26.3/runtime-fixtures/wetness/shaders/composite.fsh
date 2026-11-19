#version 120
/* DRAWBUFFERS:7 */
const int colortex7Format=RGBA32F;
const bool colortex7Clear=false;
const float wetnessHalflife=10.0;
const float drynessHalflife=20.0;
// 0=positive; 1=instant-rain substitution; 2=double advancement.
#define NEGATIVE_MODE 0 // [0 1 2]
uniform sampler2D colortex7;
uniform float wetness;
uniform float rainStrength;
uniform float frameTime;
uniform int frameCounter;
void main(){
    vec4 old=texelFetch(colortex7,ivec2(0),0);
    bool previous=old.a==.375&&abs(old.g-float(frameCounter-1))<.1;
    // Independent reference, not wetness fed back into its own oracle.
    float halfLife=rainStrength>old.r?1.0:2.0;
    float expected=previous?mix(old.r,rainStrength,1.0-exp2(-frameTime/halfLife)):rainStrength;
    float observed=wetness;
#if NEGATIVE_MODE == 1
    observed=rainStrength;
#elif NEGATIVE_MODE == 2
    observed=previous?mix(old.r,rainStrength,1.0-exp2(-2.0*frameTime/halfLife)):rainStrength;
#endif
    bool healthy=abs(expected-observed)<.00002&&(!previous||old.b>=0.0);
    // 1/2 latch demonstrated rising/falling lag; 3 means both exercised.
    int coverage=previous?int(max(old.b,0.0)):0;
    if(rainStrength-wetness>.02)coverage=coverage|1;
    if(wetness-rainStrength>.02)coverage=coverage|2;
    gl_FragColor=vec4(expected,float(frameCounter),healthy?float(coverage):-1.0,.375);
}
