#include "camera.glsl"
uniform sampler2D colortex4;
uniform sampler2D colortex7;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
bool differs(vec3 a,vec3 b){return any(greaterThan(abs(a-b),vec3(.005)));}
bool badNative(){
    vec3 expected=marker(previousCameraPosition);
    bool bad=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec4 shadow=texture2D(shadowcolor0,(vec2(x,y)+.5)/4.0);
        if(shadow.a>.999)bad=bad||differs(shadow.rgb,expected);
    }
    vec4 scene=texture2D(colortex4,texcoord);
    if(scene.a>.999)bad=bad||differs(scene.rgb,expected);
    return bad;
}
bool hasShadow(){
    bool populated=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++)
        populated=populated||texture2D(shadowcolor0,(vec2(x,y)+.5)/4.0).a>.999;
    return populated;
}
