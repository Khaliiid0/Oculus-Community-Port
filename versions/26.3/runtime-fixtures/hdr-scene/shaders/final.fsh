#version 120
uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
void main(){
    bool bad=false,terrain=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        // The runtime color sampler is LINEAR. Sample actual texel centers so
        // edges against entities/sky cannot manufacture a false HDR failure.
        vec2 size=vec2(viewWidth,viewHeight);
        vec2 pixel=floor((vec2(x,y)+.5)*size/16.)+.5;
        vec4 value=texture2D(colortex0,pixel/size);
        bool marker=value.r<.1&&value.g>.9&&value.b>.9&&value.a>.9;
        terrain=terrain||marker;
        bad=bad||marker&&any(greaterThan(abs(value-vec4(-.5,2.,4.,1.5)),vec4(.01)));
    }
    gl_FragColor=bad?vec4(1,0,1,1):terrain?vec4(0,1,0,1):vec4(1,.5,0,1);
}
