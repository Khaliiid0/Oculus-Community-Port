#version 330 compatibility
/* DRAWBUFFERS:0 */
uniform float darknessLightFactor,darknessFactor;
in vec2 darknessFromVertex;
void main(){
    bool bad=any(greaterThan(abs(darknessFromVertex-vec2(darknessLightFactor,darknessFactor)),vec2(.001)));
    gl_FragColor=vec4(darknessLightFactor,darknessFactor,bad?.75:.25,.375);
}
