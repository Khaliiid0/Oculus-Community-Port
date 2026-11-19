uniform sampler2D gaux2;
void main(){
    vec3 previous=texture2D(gaux2,vec2(.5)).rgb;
    gl_FragColor=previous.g>.9 && previous.r<.1 && previous.b<.1?vec4(0,1,0,1):vec4(1,0,0,1);
}
