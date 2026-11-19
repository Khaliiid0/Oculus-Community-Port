uniform float far;
varying float observedFar;
void main(){
    bool valid=far>0.0 && abs(observedFar-far)<0.1;
    gl_FragColor=!valid?vec4(1,0,1,1):abs(far-128.0)<0.1?vec4(1,0,0,1):abs(far-256.0)<0.1?vec4(0,1,0,1):vec4(0,0,1,1);
}
