uniform sampler2D depthtex1;
uniform sampler2D noisetex;
uniform float viewWidth;
uniform float viewHeight;
vec4 readDepth(vec3 a, vec3 b, vec3 c, vec3 d, float distance, float z0,
               sampler2D image, vec2 uv, out float sampled, int steps) {
    sampled=0.0;
    for(int i=0;i<steps;i++) sampled+=texture2D(image,uv).r;
    return vec4(sampled/float(steps));
}
void main(){
    vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    float direct=texture2D(depthtex1,uv).r;
    float sampled;
    float helper=readDepth(vec3(0),vec3(sin(1.),2.,3.),vec3(0),vec3(0),length(vec3(1)),0.,depthtex1,uv,sampled,2).r;
    vec2 noiseUV=vec2(.25,.75);
    float rawNoise=texture2D(noisetex,noiseUV).r;
    float helperNoise=readDepth(vec3(0),vec3(sin(1.),2.,3.),vec3(0),vec3(0),length(vec3(1)),0.,noisetex,noiseUV,sampled,2).r;
#ifdef NEGATIVE_SEVENTH_DEPTH
    helper+=.125;
#endif
    float surface=gl_FragCoord.z;
    bool equalReads=abs(direct-helper)<0.000001&&abs(rawNoise-helperNoise)<0.000001;
    bool behind=direct>surface+0.000001;
    vec3 result=equalReads&&behind?vec3(1.0,0.5,0.0):vec3(1.0,0.0,1.0);
    if(equalReads&&!behind)result=abs(direct-surface)<=0.000001?vec3(1.0,1.0,0.0):vec3(1.0,0.0,0.0);
    if(equalReads&&direct>0.999999)result=vec3(0.0,0.75,1.0);
    gl_FragColor=vec4(result,1.0);
}
