#version 120
/* DRAWBUFFERS:0 */
#define COMPARE_PROBE 1
#define HELPER_PROBE 1
#if COMPARE_PROBE
uniform sampler2DShadow shadowtex1;
uniform sampler2D shadowtex0;
#else
uniform sampler2D shadowtex0;
#endif
uniform sampler2D shadowcolor0;
uniform int frameCounter;
const float expectedOffset=0.0;
#if COMPARE_PROBE && HELPER_PROBE
float legacyProbe(sampler2DShadow image,vec3 position){return shadow2D(image,position).r;}
float modernProbe(sampler2DShadow image,vec3 position){return texture(image,position);}
#endif
void main(){
bool bad=false;int populated=0;
for(int i=0;i<64;i++){
vec2 uv=(vec2(float(i%8),float(i/8))+.5)/8.0;
vec4 marker=texture2D(shadowcolor0,uv);
if(marker.a>.999){
int actual=int(round(marker.g*255.0))+256*int(round(marker.b*255.0));
bad=bad||actual!=frameCounter%65536;
float rawDepth=texture2D(shadowtex0,uv).r;
bad=bad||abs(rawDepth+marker.r-1.0)>.006;
#if COMPARE_PROBE
#if HELPER_PROBE
float low=legacyProbe(shadowtex1,vec3(uv,0.0));
float high=legacyProbe(shadowtex1,vec3(uv,1.0));
#else
float low=shadow2D(shadowtex1,vec3(uv,0.0)).r;
float high=shadow2D(shadowtex1,vec3(uv,1.0)).r;
#endif
bad=bad||abs(low-(1.0+expectedOffset))>.001;
if(marker.r>.02 && marker.r<.98) {
populated++;
#if HELPER_PROBE
float nearValue=modernProbe(shadowtex1,vec3(uv,1.0-marker.r-.01));
float farValue=modernProbe(shadowtex1,vec3(uv,1.0-marker.r+.01));
#else
float nearValue=texture(shadowtex1,vec3(uv,1.0-marker.r-.01));
float farValue=texture(shadowtex1,vec3(uv,1.0-marker.r+.01));
#endif
bad=bad||abs(high-expectedOffset)>.001;
bad=bad||abs(nearValue-(1.0+expectedOffset))>.001;
bad=bad||abs(farValue-expectedOffset)>.001;
}
#else
populated++;
float depth=texture2D(shadowtex0,uv).r;
bad=bad||abs(depth+marker.r-(1.0+expectedOffset))>.006;
#endif
}}
gl_FragColor=bad?vec4(1,0,1,.375):populated>0?vec4(0,1,0,.375):vec4(1,.5,0,.375);
}
