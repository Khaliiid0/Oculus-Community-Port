#version 330 compatibility
/* DRAWBUFFERS:0 */
uniform vec3 skyColor;
in vec3 skyFromVertex;
void main(){
    bool bad=any(greaterThan(abs(skyFromVertex-skyColor),vec3(.001)));
    gl_FragData[0]=vec4(skyColor,bad?.75:.375);
}
