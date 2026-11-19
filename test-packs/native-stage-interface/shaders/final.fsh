#version 120
uniform sampler2D colortex4;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
bool wrong(vec4 payload){
    return payload.a>.999&&any(greaterThan(abs(payload.rgb-vec3(.25,.5,.75)),vec3(.005)));
}
void main(){
    bool bad=wrong(texture2D(colortex4,texcoord));
    bool hasTerrain=false,hasShadow=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 uv=(vec2(x,y)+.5)/4.;
        vec4 terrain=texture2D(colortex4,uv);
        vec4 shadow=texture2D(shadowcolor0,uv);
        bad=bad||wrong(terrain)||wrong(shadow);
        hasTerrain=hasTerrain||terrain.a>.999;
        hasShadow=hasShadow||shadow.a>.999;
    }
    gl_FragColor=bad?vec4(1,0,1,1):hasTerrain&&hasShadow?vec4(0,1,0,1):vec4(1,.5,0,1);
}
