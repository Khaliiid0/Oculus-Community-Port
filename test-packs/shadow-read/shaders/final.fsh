#version 120
uniform sampler2D colortex0;
uniform vec3 cameraPosition;
varying vec2 texcoord;
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

void main(){vec4 marker=readMarker(texcoord);
if(cameraPosition.y>1000.0) {
gl_FragColor=any(greaterThan(abs(marker),vec4(.001)))?vec4(1,0,1,1):vec4(0,1,0,1);
} else gl_FragColor=badMarker(marker)?vec4(1,0,1,1):texture2D(colortex0,texcoord);}
