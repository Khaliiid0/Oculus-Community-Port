#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D composite;
void main() {
    vec4 original = texture2D(colortex0, texcoord);
    vec4 encoded = texture2D(composite, texcoord);
    vec4 restored = vec4(encoded.gbr, encoded.a);
    gl_FragColor = (original + restored) * 0.5;
}
