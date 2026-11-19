uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor1;
uniform int frameCounter;
uniform vec3 cameraPosition;
int value(vec4 m) {
    return int(round(m.r*255.0))+256*int(round(m.g*255.0))+65536*int(round(m.b*255.0));
}
// texelFetch avoids interpolation between an empty pixel and a packed marker.
bool badPair(ivec2 pixel, bool emptyExpected) {
    vec4 a=texelFetch(shadowcolor0,pixel,0);
#ifdef NEGATIVE_ALIAS_TEST
    vec4 b=texelFetch(shadowcolor0,pixel,0);
#else
    vec4 b=texelFetch(shadowcolor1,pixel,0);
#endif
    bool emptyA=all(lessThan(abs(a),vec4(.001)));
    bool emptyB=all(lessThan(abs(b),vec4(.001)));
    if(emptyExpected)return !emptyA||!emptyB;
    if(emptyA&&emptyB)return false;
    return a.a<.999||b.a<.999||value(a)!=frameCounter%720720||value(b)!=(frameCounter+7919)%720720;
}
vec4 pairResult() {
    bool bad=false;
    bool populated=false;
    bool emptyExpected=cameraPosition.y>1000.0;
    ivec2 size=textureSize(shadowcolor0,0);
    for(int y=0;y<4;y++)for(int x=0;x<4;x++) {
        ivec2 pixel=clamp(ivec2(vec2(size)*(vec2(x,y)+.5)/4.0),ivec2(0),size-ivec2(1));
        bad=bad||badPair(pixel,emptyExpected);
        populated=populated||texelFetch(shadowcolor0,pixel,0).a>.999;
    }
    if(bad)return vec4(1,0,1,1);
    if(!emptyExpected&&!populated)return vec4(1,.5,0,1);
    return vec4(0,1,0,1);
}
