#version 120
#include "inputs.glsl"
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor1;
varying vec2 texcoord;
void main(){
    bool bad=false;
    bool populated=false;
    for(int y=0;y<4;y++)for(int x=0;x<4;x++){
        vec2 p=(vec2(x,y)+.5)/4.0;
        vec4 a=texture2D(shadowcolor0,p),b=texture2D(shadowcolor1,p);
        if(a.a>.999){
            populated=true;
            bad=bad||any(greaterThan(abs(a.rgb-inputs().rgb),vec3(.005)))
                ||abs(b.r-maxBlindnessDarkness)>.005;
        }
    }
    vec4 scene=texture2D(colortex0,texcoord);
    if(texcoord.x<.5)gl_FragColor=bad?vec4(1,0,1,1):populated?vec4(0,1,0,1):vec4(1,.5,0,1);
    else if(abs(scene.a-.875)<.005)gl_FragColor=any(greaterThan(abs(scene.rgb-inputs().rgb),vec3(.005)))?vec4(1,0,1,1):vec4(0,1,0,1);
    else gl_FragColor=scene;
}
