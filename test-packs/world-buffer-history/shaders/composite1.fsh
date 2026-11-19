#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D colortex7;
uniform sampler2D colortex9;
void main() {
    float age = texture2D(colortex7, texcoord).r;
    bool valid = texture2D(colortex9, texcoord).r == 1.0;
    // Intentional whole-scene diagnostic, never bundled in the public mod.
    // Blue for 120 warm-up frames; normal scene afterwards; magenta means failure.
    gl_FragColor = !valid ? vec4(1.0, 0.0, 1.0, 1.0)
        : age < 120.0 ? vec4(0.0, 0.2, 1.0, 1.0) : texture2D(colortex0, texcoord);
}
