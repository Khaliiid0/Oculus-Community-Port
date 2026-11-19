#version 120
uniform sampler2D colortex0;
void main(){
    bool bad=false,terrain=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        vec4 value=texture2D(colortex0,(vec2(x,y)+.5)/16.);
        bool marker=value.r<.1&&value.g>.9&&value.b>.9&&value.a>.9;
        terrain=terrain||marker;
        bad=bad||marker&&any(greaterThan(abs(value-vec4(-.5,2.,4.,1.5)),vec4(.01)));
    }
    gl_FragColor=bad?vec4(1,0,1,1):terrain?vec4(0,1,0,1):vec4(1,.5,0,1);
}
