flat in int material;
flat in vec3 basisA,basisB;
in vec2 uvMarker;
centroid noperspective in vec2 screenMarker;
void compare(in vec3 pos,in vec3 expected,inout float failure){
    if(any(greaterThan(abs(pos-expected),vec3(.0001))))failure=1.;
}
vec4 payload(){
    float failure=material==7?0.:1.;
    compare(basisA,vec3(.25,.5,.75),failure);
    compare(basisB,vec3(.75,.5,.25),failure);
    compare(vec3(uvMarker,0),vec3(.125,.875,0),failure);
    compare(vec3(screenMarker,0),vec3(.375,.625,0),failure);
    return vec4(failure>.5?vec3(1,0,1):vec3(.25,.5,.75),1);
}
