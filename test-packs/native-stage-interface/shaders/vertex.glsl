flat out int material;
flat out vec3 basisA,basisB;
out vec2 uvMarker;
noperspective centroid out vec2 screenMarker;
void supply(const in vec3 pos,out vec3 result,inout float factor){result=pos*factor;factor=7.;}
void main(){
    float factor=1.;
    supply(vec3(.25,.5,.75),basisA,factor);
    basisB=vec3(.75,.5,.25);
    material=int(factor);
#ifdef NEGATIVE_STAGE_VALUE
    material+=1;
#endif
    uvMarker=vec2(.125,.875);
    screenMarker=vec2(.375,.625);
    gl_Position=ftransform();
}
