#version 120
/* RENDERTARGETS:3 */
varying vec2 texcoord;
uniform sampler2D colortex0;
void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    gl_FragColor = vec4(scene.brg, scene.a);
}
