#version 120
uniform ivec2 atlasSize;
uniform int heldItemId,heldItemId2;
uniform sampler2D colortex4;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
bool knownId(int id){return id==40008||id==44002||id==44010||id==77;}
float code(int id){return id==40008?.125:id==44002?.375:id==44010?.625:id==77?.875:0.;}
bool wrong(vec4 value){
    return value.a>.999&&any(greaterThan(abs(value.rgb-vec3(code(heldItemId),code(heldItemId2),.75)),vec3(.005)));
}
void main(){
    bool bad=any(lessThanEqual(atlasSize,ivec2(0)))||!knownId(heldItemId)||!knownId(heldItemId2)
        ||wrong(texture2D(colortex4,texcoord));
#ifdef EXPECT_MAIN
    bad=bad||heldItemId!=EXPECT_MAIN;
#endif
#ifdef EXPECT_OFF
    bad=bad||heldItemId2!=EXPECT_OFF;
#endif
    bool hasTerrain=false,hasShadow=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=(vec2(x,y)+.5)/4.;
        vec4 terrain=texture2D(colortex4,uv),shadow=texture2D(shadowcolor0,uv);
        bad=bad||wrong(terrain)||wrong(shadow);
        hasTerrain=hasTerrain||terrain.a>.999;hasShadow=hasShadow||shadow.a>.999;
    }
    gl_FragColor=bad?vec4(1,0,1,1):hasTerrain&&hasShadow?vec4(0,1,0,1):vec4(1,.5,0,1);
}
