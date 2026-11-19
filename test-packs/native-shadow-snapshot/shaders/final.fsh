#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor0;
const float expectedOffset=0.0;
void main(){
    float coverage=0.0;
    bool bad=false;
    for(int i=0;i<64;i++) {
        vec2 uv=(vec2(float(i%8),float(i/8))+.5)/8.0;
        ivec2 pixel=ivec2(uv*512.0);
        vec4 marker=texelFetch(shadowcolor0,pixel,0);
        if(marker.a>.999 && marker.r>.02 && marker.r<.98){
        float liveDepth=texelFetch(shadowtex0,pixel,0).r;
        float opaqueDepth=texelFetch(shadowtex1,pixel,0).r;
        coverage+=1.0;
        bad=bad||abs(liveDepth-opaqueDepth)>.00001;
        bad=bad||abs(liveDepth+marker.r-(1.0+expectedOffset))>.006;
        bad=bad||abs(opaqueDepth+marker.r-(1.0+expectedOffset))>.006;
        }
    }
    gl_FragColor=bad?vec4(1.0,0.0,1.0,1.0):
        coverage<1.0?vec4(1.0,0.5,0.0,1.0):texture2D(colortex0,texcoord);
}
