#version 120
/*
const int colortex7Format = RGBA16F;
const bool colortex7Clear = false;
const vec4 colortex7ClearColor = vec4(0.0, 0.25, -0.5, 2.0);
const int colortex8Format = RGB8_SNORM;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(-1.0, 0.0, 1.0, 0.0);
*/
/* DRAWBUFFERS:79 */
varying vec2 texcoord;
uniform sampler2D colortex1;
uniform sampler2D colortex7;
uniform sampler2D colortex8;
void main() {
    vec4 history = texture2D(colortex7, texcoord);
    vec4 cleared = texture2D(colortex8, texcoord);
    vec4 depthDefault = texture2D(colortex1, texcoord);
    bool valid = history.r >= 0.0 && history.r <= 1024.0
        && history.g == 0.25 && history.b == -0.5 && history.a == 2.0
        && cleared.r == -1.0 && cleared.g == 0.0 && cleared.b == 1.0 && cleared.a == 1.0
        && all(equal(depthDefault, vec4(1.0)));
    gl_FragData[0] = vec4(min(history.r + 1.0, 1024.0), history.gba);
    gl_FragData[1] = vec4(valid ? 1.0 : 0.0);
}
