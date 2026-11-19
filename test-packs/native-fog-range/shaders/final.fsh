#version 120
uniform sampler2D colortex4,colortex6;
uniform sampler2D shadowcolor0,shadowcolor1;
#ifdef EXPECT_MEDIUM
uniform int isEyeInWater;
#endif
varying vec2 texcoord;
varying vec4 fogFromPostVertex;
bool marker(vec4 v){return abs(v.a-.375)<.01&&(abs(v.b-.25)<.01||abs(v.b-.75)<.01);}
bool wrong(vec4 v,vec4 color){
    vec2 expected=vec2(fract(gl_Fog.start/257.+.5),fract(gl_Fog.end/263.+.5));
    return marker(v)&&(v.b>.5||any(greaterThan(abs(v.rg-expected),vec2(.01)))
        ||any(greaterThan(abs(color-gl_Fog.color),vec4(.01))));
}
void main(){
    vec4 expected=vec4(gl_Fog.start,gl_Fog.end,gl_Fog.scale,gl_Fog.color.a);
    bool bad=any(greaterThan(abs(fogFromPostVertex-expected),vec4(.001)))
        ||gl_Fog.end<=gl_Fog.start||gl_Fog.color.a<=0.
        ||wrong(texture2D(colortex4,texcoord),texture2D(colortex6,texcoord));
#ifdef EXPECT_MEDIUM
    bad=bad||isEyeInWater!=EXPECT_MEDIUM;
#endif
#ifdef EXPECT_START
    bad=bad||abs(gl_Fog.start-EXPECT_START)>.01;
#endif
#ifdef EXPECT_END
    bad=bad||abs(gl_Fog.end-EXPECT_END)>.01;
#endif
    bool hasTerrain=false,hasShadow=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=(vec2(x,y)+.5)/4.;
        vec4 terrain=texture2D(colortex4,uv),shadow=texture2D(shadowcolor0,uv);
        bad=bad||wrong(terrain,texture2D(colortex6,uv))||wrong(shadow,texture2D(shadowcolor1,uv));
        hasTerrain=hasTerrain||marker(terrain);hasShadow=hasShadow||marker(shadow);
    }
    gl_FragColor=bad?vec4(1,0,1,1):hasTerrain&&hasShadow?vec4(0,1,0,1):vec4(1,.5,0,1);
}
