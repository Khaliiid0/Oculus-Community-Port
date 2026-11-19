#version 120
uniform float darknessLightFactor,darknessFactor;
uniform sampler2D colortex4;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
bool marker(vec4 v){return abs(v.a-.375)<.01&&(abs(v.b-.25)<.01||abs(v.b-.75)<.01);}
bool wrong(vec4 v){return marker(v)&&(v.b>.5||any(greaterThan(abs(v.rg-vec2(darknessLightFactor,darknessFactor)),vec2(.01))));}
void main(){
    bool bad=darknessLightFactor<0.||darknessLightFactor>.451||wrong(texture2D(colortex4,texcoord));
#ifdef EXPECT_CLEAR
    bad=bad||darknessFactor!=0.||darknessLightFactor!=0.;
#endif
#ifdef EXPECT_ACTIVE
    bad=bad||darknessFactor<.99||abs(darknessFactor-darknessLightFactor)<.1;
#endif
#ifdef EXPECT_PULSE
    bad=bad||darknessLightFactor<.08;
#endif
    bool hasTerrain=false,hasShadow=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=(vec2(x,y)+.5)/4.;vec4 terrain=texture2D(colortex4,uv),shadow=texture2D(shadowcolor0,uv);
        bad=bad||wrong(terrain)||wrong(shadow);
        hasTerrain=hasTerrain||marker(terrain);hasShadow=hasShadow||marker(shadow);
    }
    gl_FragColor=bad?vec4(1,0,1,1):hasTerrain&&hasShadow?vec4(darknessLightFactor*.8,1,0,1):vec4(1,.5,0,1);
}
