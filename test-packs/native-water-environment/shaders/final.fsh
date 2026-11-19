uniform sampler2D colortex0;
uniform int isEyeInWater;
uniform float darknessFactor;
uniform vec3 fogColor;
varying vec2 texcoord;
void main(){
    vec3 nativeValue=texture2D(colortex0,texcoord).rgb;
    vec3 expected=vec3(float(isEyeInWater)/3.0,darknessFactor,fogColor.r);
    bool valid=all(lessThan(abs(nativeValue-expected),vec3(0.015)));
    vec3 marker=vec3(1.0,0.5,0.0);
    if(isEyeInWater==1)marker=vec3(0.0,0.6,1.0);
    if(isEyeInWater==2)marker=vec3(1.0,0.2,0.0);
    if(isEyeInWater==3)marker=vec3(0.8,0.8,1.0);
    if(darknessFactor>0.1)marker=vec3(0.55,0.2,1.0);
    gl_FragColor=vec4(valid?marker:vec3(1.0,0.0,1.0),1.0);
}
