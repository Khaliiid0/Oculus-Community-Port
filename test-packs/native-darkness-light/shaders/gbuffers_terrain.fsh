#version 330 compatibility
/* DRAWBUFFERS:04 */
uniform float darknessLightFactor,darknessFactor;
in vec2 darknessFromVertex;
void main(){
    bool bad=any(greaterThan(abs(darknessFromVertex-vec2(darknessLightFactor,darknessFactor)),vec2(.001)));
    vec4 value=vec4(darknessLightFactor,darknessFactor,bad?.75:.25,.375);
    gl_FragData[0]=value;gl_FragData[1]=value;
}
