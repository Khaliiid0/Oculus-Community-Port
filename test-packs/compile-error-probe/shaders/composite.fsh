#version 330 compatibility
/* RENDERTARGETS: 0 */
uniform sampler2D colortex3;
varying vec2 texcoord;
void main() {
    vec4 encoded = texture2D(colortex3, texcoord);
    gl_FragColor = vec4(encoded.gbr, encoded.a);
}
