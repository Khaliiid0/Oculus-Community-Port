#version 120
uniform sampler2D colortex0;
uniform float viewWidth;
// Inspect the first failing sample using four grayscale bands (R,G,B,A).
// Decoding: channel = (displayed / 255 - .5) * 8, subject to RGBA8 rounding.
// This is bounded diagnostic output, not a precision readback or normal view.
void main(){
    bool bad=false,terrain=false;
    vec4 observed=vec4(0.);
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        vec4 value=texture2D(colortex0,(vec2(x,y)+.5)/16.);
        bool marker=value.r<.1&&value.g>.9&&value.b>.9&&value.a>.9;
        terrain=terrain||marker;
        if(!bad&&marker&&any(greaterThan(abs(value-vec4(-.5,2.,4.,1.5)),vec4(.01)))){
            observed=value;bad=true;
        }
    }
    float band=gl_FragCoord.x/viewWidth;
    float component=band<.25?observed.r:band<.5?observed.g:band<.75?observed.b:observed.a;
    gl_FragColor=bad?vec4(vec3(clamp(.5+component/8.,0.,1.)),1.)
        :terrain?vec4(0,1,0,1):vec4(1,.5,0,1);
}
