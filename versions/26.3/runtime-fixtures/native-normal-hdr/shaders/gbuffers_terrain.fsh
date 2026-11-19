#version 120
/* DRAWBUFFERS:0 */
const int colortex0Format=R11F_G11F_B10F;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
void main(){
    vec4 albedo=texture2D(texture,uv)*tint;
    if(albedo.a<.1)discard;
    gl_FragData[0]=vec4(albedo.rgb*texture2D(lightmap,lm).rgb,albedo.a);
}
