#version 120
uniform sampler2D colortex4,shadowcolor0;
uniform vec3 skyColor;
varying vec3 skyFromPostVertex;
bool marker(vec4 value){return abs(value.a-.375)<.01||abs(value.a-.75)<.01;}
bool wrong(vec4 value){
    return marker(value)&&(value.a>.5||any(greaterThan(abs(value.rgb-skyColor),vec3(.01))));
}
void main(){
    bool bad=any(greaterThan(abs(skyFromPostVertex-skyColor),vec3(.001)));
#ifdef REQUIRE_NONZERO_SKY
    bad=bad||dot(skyColor,vec3(1))<.01;
#endif
    bool terrain=false,shadow=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        vec2 uv=(vec2(x,y)+.5)/16.;
        vec4 t=texture2D(colortex4,uv),s=texture2D(shadowcolor0,uv);
        bad=bad||wrong(t)||wrong(s);
        terrain=terrain||marker(t);shadow=shadow||marker(s);
    }
#ifdef SHOW_SKY_RGB
    gl_FragColor=vec4(skyColor,1);
#else
    gl_FragColor=bad?vec4(1,0,1,1):terrain&&shadow?vec4(0,1,0,1):vec4(1,.5,0,1);
#endif
}
