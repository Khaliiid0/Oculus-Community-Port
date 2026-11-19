#version 330 compatibility
/* DRAWBUFFERS:04 */
uniform vec3 skyColor;
in vec3 skyFromVertex;
void main(){
    bool bad=any(greaterThan(abs(skyFromVertex-skyColor),vec3(.001)));
    vec4 marker=vec4(skyColor,bad?.75:.375);
    gl_FragData[0]=marker;gl_FragData[1]=marker;
}
