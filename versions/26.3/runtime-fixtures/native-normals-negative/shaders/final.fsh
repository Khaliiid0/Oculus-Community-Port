#version 120
#include "/mode.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D shadowcolor0;
uniform float viewWidth;
uniform float viewHeight;
bool marker(vec4 v){return abs(v.a-.375)<.01&&abs(v.b-.125)<.01;}
void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    if(pixel.x>=int(viewWidth*.22)||pixel.y<=int(viewHeight*.75)){gl_FragColor=texelFetch(colortex0,pixel,0);return;}
    bool bad=false,terrain=false,shadow=false,mappedTerrain=false,mappedShadow=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        ivec2 tp=ivec2((vec2(x,y)+.5)*vec2(viewWidth,viewHeight)/16.0);
        ivec2 sp=ivec2((vec2(x,y)+.5)*vec2(textureSize(shadowcolor0,0))/16.0);
        vec4 t=texelFetch(colortex4,tp,0),s=texelFetch(shadowcolor0,sp,0);
        bad=bad||(marker(t)&&t.r>.5)||(marker(s)&&s.r>.5);
        terrain=terrain||marker(t);shadow=shadow||marker(s);
        mappedTerrain=mappedTerrain||(marker(t)&&t.g>.5);mappedShadow=mappedShadow||(marker(s)&&s.g>.5);
    }
#if REQUIRE_NORMAL_MAP
    terrain=terrain&&mappedTerrain;shadow=shadow&&mappedShadow;
#else
    bad=bad||mappedTerrain||mappedShadow;
#endif
    vec4 panel=bad?vec4(1,0,1,1):terrain&&shadow?vec4(0,.75,1,1):vec4(1,1,0,1);
    gl_FragColor=panel;
}
