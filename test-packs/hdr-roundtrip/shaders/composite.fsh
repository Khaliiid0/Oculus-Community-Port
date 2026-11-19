#version 120
/*
const int colortex7Format = RGBA16F;
const int colortex8Format = RGB8_SNORM;
const int colortex9Format = RGB16F;
const int colortex10Format = RGB8;
*/
/* RENDERTARGETS:7,8,9,10 */
varying vec2 texcoord;
uniform sampler2D colortex0;
void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;
    gl_FragData[0] = vec4(scene * 4.0 - 1.0, 2.0);
    gl_FragData[1] = vec4(-0.5, 0.5, 1.0, -1.0);
    gl_FragData[2] = vec4(-2.0, 4.0, 0.5, -1.0);
    gl_FragData[3] = vec4(0.25, 0.5, 0.75, 0.0);
}
