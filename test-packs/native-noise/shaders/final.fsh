#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
uniform sampler2D noisetex;
bool invalidNoise() {
    return any(notEqual(textureSize(noisetex,0),ivec2(2,2)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(0,0),0)-vec4(17,67,131,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(1,0),0)-vec4(211,31,89,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(0,1),0)-vec4(43,173,7,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(1,1),0)-vec4(101,229,53,255)/255.0),vec4(0.002)));
}

void main(){
ivec2 size=textureSize(shadowcolor0,0);
vec4 marker=texelFetch(shadowcolor0,clamp(ivec2(texcoord*vec2(size)),ivec2(0),size-ivec2(1)),0);
bool bad=marker.a>0.999 && (marker.r>0.5 || marker.g<0.5 || marker.b>0.5);
gl_FragColor=(invalidNoise()||bad)?vec4(1,0,1,1):texture2D(colortex0,texcoord);
}
