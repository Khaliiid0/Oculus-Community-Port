#version 120
/* DRAWBUFFERS:0 */
uniform sampler2D texture;
varying vec2 uv;
uniform sampler2D noisetex;
bool invalidNoise() {
    return any(notEqual(textureSize(noisetex,0),ivec2(2,2)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(0,0),0)-vec4(17,67,131,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(1,0),0)-vec4(211,31,89,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(0,1),0)-vec4(43,173,7,255)/255.0),vec4(0.002)))
        || any(greaterThan(abs(texelFetch(noisetex,ivec2(1,1),0)-vec4(101,229,53,255)/255.0),vec4(0.002)));
}

void main(){vec4 a=texture2D(texture,uv); if(a.a<0.1)discard;
gl_FragColor=invalidNoise()?vec4(1,0,1,1):vec4(a.rgb*vec3(0.4,1,0.5),1);}
