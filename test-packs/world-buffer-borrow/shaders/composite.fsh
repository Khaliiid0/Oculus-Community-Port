#version 120
/*
const int colortex7Format = RGBA16F;
const int colortex8Format = RGB8_SNORM;
const int colortex9Format = RGBA16F;
*/
/* DRAWBUFFERS:79 */
varying vec2 texcoord;
uniform sampler2D colortex7;
uniform sampler2D colortex8;
void main() {
    vec4 worldValue = texture2D(colortex7, texcoord);
    vec4 cleared = texture2D(colortex8, texcoord);
    bool valid = worldValue.r >= 0.0 && worldValue.r < 0.041
        && cleared.a == 1.0 && dot(abs(cleared.rgb), vec3(1.0)) < 0.001;
    gl_FragData[0] = vec4(worldValue.rgb + 1.0, 2.0);
    gl_FragData[1] = vec4(worldValue.rgb, valid ? 1.0 : 0.0);
}
