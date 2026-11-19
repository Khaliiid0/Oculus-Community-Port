#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D composite;
uniform sampler2D colortex5;
void main() {
    vec4 encoded = texture2D(composite, texcoord);
    vec4 mask = texture2D(colortex5, texcoord * 3.0);
    gl_FragColor = vec4(encoded.gbr, encoded.a) * mask;
}
