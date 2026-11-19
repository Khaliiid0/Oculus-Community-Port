#version 120
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex7;
uniform sampler2D shadowcolor0;
uniform float wetness;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
varying float postVertexWetness;
bool marked(vec4 value){return abs(value.a-.375)<.003&&abs(value.b-.125)<.003;}
void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    if(pixel.x>=int(viewWidth*.22)||pixel.y<=int(viewHeight*.75)){
        gl_FragColor=texelFetch(colortex0,pixel,0);return;
    }
    vec4 history=texelFetch(colortex7,ivec2(0),0);
    bool bad=history.a!=.375||history.b<0.0||abs(history.r-wetness)>.00002
        ||abs(postVertexWetness-wetness)>.00002;
    bool terrain=false,shadow=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        ivec2 tp=ivec2((vec2(x,y)+.5)*vec2(viewWidth,viewHeight)/16.0);
        ivec2 sp=ivec2((vec2(x,y)+.5)*vec2(textureSize(shadowcolor0,0))/16.0);
        vec4 t=texelFetch(colortex4,tp,0),s=texelFetch(shadowcolor0,sp,0);
        terrain=terrain||marked(t);shadow=shadow||marked(s);
        bad=bad||(marked(t)&&(abs(t.r-wetness)>.00002||abs(t.g-wetness)>.00002))
            // Shadow attachments are explicitly RGBA8: allow half an UNORM8 LSB plus arithmetic error.
            ||(marked(s)&&(abs(s.r-wetness)>.0021||abs(s.g-wetness)>.0021));
    }
    // Top strip shows instantaneous rain (white) and wetness (blue) as separate bars.
    if(pixel.y>int(viewHeight*.95)){
        float value=pixel.y>int(viewHeight*.975)?rainStrength:wetness;
        gl_FragColor=float(pixel.x)/viewWidth<.22*value?vec4(.3,.6,1,1):vec4(.05,.05,.05,1);return;
    }
    gl_FragColor=bad?vec4(1,0,1,1):terrain&&shadow&&history.b>2.5?vec4(0,.75,1,1):vec4(1,1,0,1);
}
