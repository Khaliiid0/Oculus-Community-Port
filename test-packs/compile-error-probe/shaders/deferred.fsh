#version 330 compatibility
/* RENDERTARGETS: 3 */
uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
varying vec2 texcoord;
void main() {
    vec2 halfPixel = 0.5 / vec2(viewWidth, viewHeight);
    vec4 scene = texture2D(colortex0, clamp(texcoord, halfPixel, 1.0 - halfPixel));
    gl_FragColor = vec4(scene.brg, scene.a);
}
