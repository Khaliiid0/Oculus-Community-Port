#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
varying vec2 texcoord;
const int expectedMode=0;
void main(){
    int changed=0;int preserved=0;int livePopulated=0;int opaquePopulated=0;bool bad=false;
    for(int i=0;i<64;i++){
        ivec2 pixel=ivec2((vec2(float(i%8),float(i/8))+.5)*64.0);
        float liveDepth=texelFetch(shadowtex0,pixel,0).r;
        float opaqueDepth=texelFetch(shadowtex1,pixel,0).r;
        if(liveDepth<.9999) livePopulated++;
        if(opaqueDepth<.9999) opaquePopulated++;
        bad=bad||liveDepth>opaqueDepth+.0001;
        if(liveDepth<opaqueDepth-.001) changed++;
        if(opaqueDepth>.02 && opaqueDepth<.98 && abs(liveDepth-opaqueDepth)<.0001) preserved++;
    }
    if(expectedMode==2){
        gl_FragColor=opaquePopulated>0?vec4(1,0,1,1):livePopulated>0?vec4(0,1,1,1):vec4(1,.5,0,1);
        return;
    }
    if(expectedMode==3){
        gl_FragColor=opaquePopulated>0||livePopulated>0?vec4(1,0,1,1):vec4(0,1,1,1);
        return;
    }
    gl_FragColor=bad?vec4(1,0,1,1):preserved<1?vec4(1,.5,0,1):
        changed<1?vec4(1,0,0,1):vec4(0,1,0,1);
}
