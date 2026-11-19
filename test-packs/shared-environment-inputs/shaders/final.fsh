#version 120
#include "values.glsl"
uniform float rainStrength;
uniform sampler2D colortex4;
uniform sampler2D colortex6;
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor1;
varying vec2 texcoord;
bool wrong(vec4 value,vec3 expected){return value.a>.999&&any(greaterThan(abs(value.rgb-expected),vec3(.006)));}
void main(){
    vec3 expectedA=vec3(rainProbe,skyProbe,snowProbe);
    vec3 expectedB=vec3(frameProbe/(1.+frameProbe),effectProbe,maxBlindnessDarkness);
    bool bad=abs(effectProbe-maxBlindnessDarkness)>.0001;
#ifdef REQUIRE_RAIN
    bad=bad||rainStrength<.9||rainProbe<.8;
#endif
#ifdef REQUIRE_SNOW
    bad=bad||snowProbe<.8;
#endif
#ifdef REQUIRE_EFFECT
    bad=bad||effectProbe<.9||maxBlindnessDarkness<.9;
#endif
    bad=bad||wrong(texture2D(colortex4,texcoord),expectedA)||wrong(texture2D(colortex6,texcoord),expectedB);
    bool hasTerrain=false,hasShadow=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=(vec2(x,y)+.5)/4.;
        vec4 terrainA=texture2D(colortex4,uv),terrainB=texture2D(colortex6,uv);
        vec4 shadowA=texture2D(shadowcolor0,uv),shadowB=texture2D(shadowcolor1,uv);
        bad=bad||wrong(terrainA,expectedA)||wrong(terrainB,expectedB)||wrong(shadowA,expectedA)||wrong(shadowB,expectedB);
        hasTerrain=hasTerrain||(terrainA.a>.999&&terrainB.a>.999);
        hasShadow=hasShadow||(shadowA.a>.999&&shadowB.a>.999);
    }
    gl_FragColor=bad?vec4(1,0,1,1):hasTerrain&&hasShadow?vec4(0,1,0,1):vec4(1,.5,0,1);
}
