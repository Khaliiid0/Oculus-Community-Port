#include "/context.glsl"
#include "/mode.glsl"
const float shadowDistance=32.0;
uniform float resourceFormatCode;
varying float vertexFormat;
varying vec2 uv;
#ifdef MC_TEXTURE_FORMAT_LAB_PBR
uniform sampler2D normals;
#endif
vec4 contextMarker(){
    float code=float(RESOURCE_FORMAT_CODE);
    bool bad=abs(vertexFormat-code)>.01||abs(resourceFormatCode-code)>.01;
#ifdef MC_TEXTURE_FORMAT_LAB_PBR
    vec4 n=texelFetch(normals,ivec2(uv*vec2(textureSize(normals,0))),0);
    bool flatDefault=all(lessThan(abs(n-vec4(127,127,255,255)/255.0),vec4(.006)));
    bool numericMap=all(lessThan(abs(n-vec4(64,64,64,32)/255.0),vec4(.006)))
        ||all(lessThan(abs(n-vec4(192,64,128,96)/255.0),vec4(.006)))
        ||all(lessThan(abs(n-vec4(64,192,192,160)/255.0),vec4(.006)))
        ||all(lessThan(abs(n-vec4(192,192,252,224)/255.0),vec4(.006)));
    bad=bad||!(flatDefault||numericMap);
#endif
#if NEGATIVE_FORMAT
    bad=true;
#endif
    return vec4(bad?.75:.25,code*.25,.125,.375);
}
