#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D composite;
void main() {
    vec4 original = texture2D(colortex0, texcoord);
    vec4 encoded = texture2D(composite, texcoord);
    gl_FragColor = (original + vec4(encoded.gbr, encoded.a)) * 0.5;
}
