uniform sampler2D colortex0;
uniform sampler2D colortex4;
varying vec2 texcoord;
void main(){
    vec4 scene=texture2D(colortex0,texcoord);
    vec3 auxiliary=texture2D(colortex4,texcoord).rgb;
    if(auxiliary.r>.9 && auxiliary.g>.9 && auxiliary.b<.1)
        gl_FragColor=scene.b>.9 && scene.r<.1 && scene.g<.1?vec4(0,1,1,1):vec4(1,0,1,1);
    else gl_FragColor=scene;
}
