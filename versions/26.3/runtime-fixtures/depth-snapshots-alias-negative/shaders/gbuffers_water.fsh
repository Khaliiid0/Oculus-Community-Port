#version 120
/* DRAWBUFFERS:04 */
const int colortex0Format=RGBA16F;
const int colortex4Format=RGBA32F;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth;
uniform float viewHeight;
float distanceAt(vec2 screen,float depth){vec4 p=gbufferProjectionInverse*vec4(screen*2.0-1.0,depth*2.0-1.0,1.0);return -p.z/p.w;}
void main(){
    vec4 a=texture2D(texture,uv)*tint;
    vec2 screen=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    float opaque=texture2D(depthtex1,screen).r;
    // Negative R and greater-than-one distances must survive the float attachment.
    gl_FragData[1]=vec4(-distanceAt(screen,gl_FragCoord.z),distanceAt(screen,opaque),gl_FragCoord.z,1.0);
    gl_FragData[0]=vec4(a.rgb*texture2D(lightmap,lm).rgb,a.a);
}
