#version 120
uniform sampler2D colortex0;
uniform sampler2D gaux1;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D depthtex2;
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth;
uniform float viewHeight;
float distanceAt(vec2 screen,float depth){vec4 p=gbufferProjectionInverse*vec4(screen*2.0-1.0,depth*2.0-1.0,1.0);return -p.z/p.w;}
void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    vec2 screen=(vec2(pixel)+0.5)/vec2(viewWidth,viewHeight);
    vec4 marker=texelFetch(gaux1,pixel,0);
    vec4 color=texelFetch(colortex0,pixel,0);
    if(marker.a<0.99){gl_FragColor=color;return;}
    float live=texelFetch(depthtex0,pixel,0).r;
    float opaque=texelFetch(depthtex1,pixel,0).r;
    float preHand=texelFetch(depthtex0,pixel,0).r; // Deliberate former alias defect.
    float surfaceDistance=distanceAt(screen,live);
    float opaqueDistance=distanceAt(screen,opaque);
    float tolerance=max(0.03,abs(marker.g)*0.001);
    bool separated=opaque>live+0.0000001&&opaqueDistance-surfaceDistance>0.15;
    bool correct=abs(preHand-opaque)<0.0000002&&abs(surfaceDistance+marker.r)<tolerance
        &&abs(opaqueDistance-marker.g)<tolerance&&abs(live-marker.b)<0.0000002;
    // Cyan=exercised/pass. Magenta=mismatch. Yellow=insufficient separation.
    gl_FragColor=!separated?vec4(1,1,0,1):correct?vec4(0,0.75,1,1):vec4(1,0,1,1);
}
