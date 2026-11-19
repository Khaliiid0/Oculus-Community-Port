#version 120
#define NEGATIVE_HDR_SCENE
/* DRAWBUFFERS:0 */
const int colortex0Format=RGBA16F;
void main(){
    vec4 value=vec4(-.5,2.,4.,1.5);
#ifdef NEGATIVE_HDR_SCENE
    value.g=1.;
#endif
    gl_FragColor=value;
}
