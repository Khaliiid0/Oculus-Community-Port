#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform vec3 cameraPosition;
uniform float eyeAltitude;
varying vec2 texcoord;
const float expectedOffset=0.0;
void main(){
vec2 uv=vec2(fract(texcoord.x*2.0),texcoord.y);
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=texcoord.x>=.5?texelFetch(shadowcolor0,clamp(ivec2(uv*vec2(size)),ivec2(0),size-ivec2(1)),0):texture2D(colortex0,uv);
vec2 expected=vec2(clamp(cameraPosition.y/512.0+cameraPosition.x/4096.0+cameraPosition.z/4096.0,0.0,1.0),clamp(eyeAltitude/512.0,0.0,1.0));
bool marked=abs(marker.a-.375)<.01&&(abs(marker.b-.25)<.01||abs(marker.b-.75)<.01);
bool bad=marker.b>.5||any(greaterThan(abs(marker.rg-expected-vec2(expectedOffset)),vec2(.006)));
gl_FragColor=!marked?vec4(0,0,0,1):(bad?vec4(1,0,1,1):vec4(0,1,0,1));
}
