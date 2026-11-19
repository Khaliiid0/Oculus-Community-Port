#version 120
uniform sampler2D colortex4,shadowcolor0;
bool marker(vec4 v){return abs(v.a-.375)<.01&&abs(v.b-.125)<.01;}
void main(){
    bool bad=false,terrain=false,shadow=false,mappedTerrain=false,mappedShadow=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        vec2 uv=(vec2(x,y)+.5)/16.;
        vec4 t=texture2D(colortex4,uv),s=texture2D(shadowcolor0,uv);
        bad=bad||marker(t)&&t.r>.5||marker(s)&&s.r>.5;
        terrain=terrain||marker(t);shadow=shadow||marker(s);
        mappedTerrain=mappedTerrain||marker(t)&&t.g>.5;
        mappedShadow=mappedShadow||marker(s)&&s.g>.5;
    }
#ifdef REQUIRE_SPECULAR_MAP
    terrain=terrain&&mappedTerrain;shadow=shadow&&mappedShadow;
#endif
    gl_FragColor=bad?vec4(1,0,1,1):terrain&&shadow?vec4(0,1,0,1):vec4(1,.5,0,1);
}
