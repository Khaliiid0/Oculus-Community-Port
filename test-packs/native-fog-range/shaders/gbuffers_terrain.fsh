#version 330 compatibility
/* DRAWBUFFERS:046 */
uniform float fogStart,fogEnd;
in vec4 fogFromVertex;
in vec3 fogColorFromVertex;
void main(){
    vec4 expected=vec4(gl_Fog.start,gl_Fog.end,gl_Fog.scale,gl_Fog.color.a);
    bool bad=any(greaterThan(abs(fogFromVertex-expected),vec4(.001)))
        ||any(greaterThan(abs(fogColorFromVertex-gl_Fog.color.rgb),vec3(.001)))
        ||abs(fogStart-gl_Fog.start)>.001||abs(fogEnd-gl_Fog.end)>.001;
    vec4 marker=vec4(fract(gl_Fog.start/257.+.5),fract(gl_Fog.end/263.+.5),bad?.75:.25,.375);
    gl_FragData[0]=marker;gl_FragData[1]=marker;gl_FragData[2]=gl_Fog.color;
}
