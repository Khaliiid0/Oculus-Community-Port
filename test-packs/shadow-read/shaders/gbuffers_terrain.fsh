#version 120
/* DRAWBUFFERS:0 */
uniform sampler2D texture;
varying vec2 uv;
uniform sampler2D shadowcolor0;
uniform int frameCounter;
const int expectedOffset=0;
bool badMarker(vec4 marker) {
    int expected=(frameCounter+expectedOffset)%720720;
    int actual=int(round(marker.r*255.0))+256*int(round(marker.g*255.0))+65536*int(round(marker.b*255.0));
    return marker.a>0.999 && actual!=expected;
}
vec4 readMarker(vec2 coords) {
    ivec2 size=textureSize(shadowcolor0,0);
    return texelFetch(shadowcolor0,clamp(ivec2(coords*vec2(size)),ivec2(0),size-ivec2(1)),0);
}

void main(){vec4 a=texture2D(texture,uv);if(a.a<0.1)discard;
bool bad=false; bool populated=false;
for(int i=0;i<16;i++) { vec4 m=readMarker(vec2(.5+float(i%4)*.125,.125+float(i/4)*.125));
bad=bad||badMarker(m); populated=populated||m.a>0.999; }
gl_FragColor=bad?vec4(1,0,1,1):vec4(a.rgb*(populated?vec3(.4,1,.5):vec3(1,.6,.1)),1);}
