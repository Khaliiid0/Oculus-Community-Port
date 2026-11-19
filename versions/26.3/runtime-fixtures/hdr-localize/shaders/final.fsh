#version 120
uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
// Spatial view of the original broad HDR marker predicate. Green is exact
// terrain; red is a sample the original predicate labels invalid. Other
// world rendering is shown with a diagnostic scale, not a normal color grade.
void main(){
    vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    vec4 value=texture2D(colortex0,uv);
    bool marker=value.r<.1&&value.g>.9&&value.b>.9&&value.a>.9;
    bool bad=marker&&any(greaterThan(abs(value-vec4(-.5,2.,4.,1.5)),vec4(.01)));
    gl_FragColor=bad?vec4(1,0,0,1):marker?vec4(0,1,0,1)
        :vec4(clamp(value.rgb*.25+.125,0.,1.),1.);
}
