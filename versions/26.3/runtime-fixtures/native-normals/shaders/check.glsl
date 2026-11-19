#include "/mode.glsl"
const float shadowDistance=32.0;
uniform sampler2D normals;
uniform sampler2D specular;
uniform ivec2 atlasSize;
varying vec2 materialUv;
bool nearColor(vec4 a,vec4 b){return all(lessThan(abs(a-b),vec4(.006)));}
vec4 normalFetch(ivec2 p){return texelFetch(normals,clamp(p,ivec2(0),textureSize(normals,0)-1),0);}
vec4 materialMarker(){
    ivec2 size=textureSize(normals,0);
    vec4 raw=normalFetch(ivec2(materialUv*vec2(size)));
    vec4 flatDefault=vec4(127,127,255,255)/255.0;
    bool mapped=!nearColor(raw,flatDefault);
    bool known=nearColor(raw,vec4(64,64,64,32)/255.0)||nearColor(raw,vec4(192,64,128,96)/255.0)
        ||nearColor(raw,vec4(64,192,192,160)/255.0)||nearColor(raw,vec4(192,192,252,224)/255.0);
    vec2 pos=materialUv*vec2(size)-0.5;
    ivec2 base=ivec2(floor(pos));vec2 f=fract(pos);
    vec4 expected=mix(mix(normalFetch(base),normalFetch(base+ivec2(1,0)),f.x),
        mix(normalFetch(base+ivec2(0,1)),normalFetch(base+ivec2(1,1)),f.x),f.y);
    vec4 filtered=texture2DLod(normals,materialUv,0.0);
#if NEGATIVE_NORMAL
    filtered.r+=0.125;
#endif
    bool full=all(equal(size,atlasSize));
    vec4 mip=full?texelFetch(normals,ivec2(materialUv*vec2(textureSize(normals,4))),4):texture2DLod(normals,materialUv,1000.0);
    vec4 spec=texture2DLod(specular,materialUv,0.0);
    bool bad=!nearColor(filtered,expected)||!nearColor(mip,mapped?vec4(128,128,159,128)/255.0:flatDefault)
        ||(mapped&&(!known||!full||!nearColor(spec,vec4(16,32,48,64)/255.0)))
        ||(!mapped&&!nearColor(spec,vec4(0)));
    return vec4(bad?.75:.25,mapped?.75:.25,.125,.375);
}
