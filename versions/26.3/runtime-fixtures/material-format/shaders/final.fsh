#version 120
#include "/context.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D shadowcolor0;
uniform float resourceFormatCode;
uniform float viewWidth;
uniform float viewHeight;
varying float postVertexFormat;
bool marker(vec4 value){return abs(value.a-.375)<.01&&abs(value.b-.125)<.01;}
void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    if(pixel.x>=int(viewWidth*.22)||pixel.y<=int(viewHeight*.75)){gl_FragColor=texelFetch(colortex0,pixel,0);return;}
    float code=float(RESOURCE_FORMAT_CODE);
    bool bad=abs(resourceFormatCode-code)>.01||abs(postVertexFormat-code)>.01;
    bool terrain=false,shadow=false;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++){
        ivec2 tp=ivec2((vec2(x,y)+.5)*vec2(viewWidth,viewHeight)/16.0);
        ivec2 sp=ivec2((vec2(x,y)+.5)*vec2(textureSize(shadowcolor0,0))/16.0);
        vec4 t=texelFetch(colortex4,tp,0),s=texelFetch(shadowcolor0,sp,0);
        bool tm=marker(t),sm=marker(s);
        terrain=terrain||tm;shadow=shadow||sm;
        bad=bad||(tm&&(t.r>.5||abs(t.g-code*.25)>.01))||(sm&&(s.r>.5||abs(s.g-code*.25)>.01));
    }
    vec4 context=code<.5?vec4(0,.75,1,1):code<1.5?vec4(1,.5,0,1):vec4(.5,.25,1,1);
    gl_FragColor=bad?vec4(1,0,1,1):terrain&&shadow?context:vec4(1,1,0,1);
}
