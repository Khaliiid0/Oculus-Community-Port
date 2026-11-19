#version 120
/* DRAWBUFFERS:0 */
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
uniform sampler2D gaux1;
uniform float far;
uniform float viewWidth;
uniform float viewHeight;
void main() {
    vec4 marker=texture2D(gaux1,gl_FragCoord.xy/vec2(viewWidth,viewHeight));
    bool valid=abs(marker.r-far)<0.5&&abs(marker.g-0.25)<0.01&&marker.a>0.99;
    vec4 albedo=texture2D(texture,uv)*tint;
    gl_FragData[0]=valid?vec4(albedo.rgb*texture2D(lightmap,lm).rgb,albedo.a):vec4(1.0,0.0,1.0,1.0);
}
