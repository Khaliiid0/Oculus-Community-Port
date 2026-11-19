uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
varying vec2 texcoord;
void main(){
    vec4 scene=texture2D(colortex0,texcoord);
    vec3 auxiliary=texture2D(colortex4,texcoord).rgb;
    if(auxiliary.r>.9 && auxiliary.g>.9 && auxiliary.b<.1)
        gl_FragColor=scene.b>.9 && scene.r<.1 && scene.g<.1?vec4(0,1,1,1):vec4(1,0,1,1);
    else if(scene.r>.9 && scene.g<.1 && scene.b<.1) {
        vec3 marker=texture2D(colortex5,texcoord).rgb;
        gl_FragColor=marker.r>.9 && marker.g<.1 && marker.b>.9?scene:vec4(1,0,1,1);
    } else if(scene.g>.9 && scene.r<.1 && scene.b<.1) {
        vec3 marker=texture2D(colortex6,texcoord).rgb;
        gl_FragColor=marker.r>.9 && marker.g>.9 && marker.b<.1?scene:vec4(1,0,1,1);
    } else gl_FragColor=scene;
}
