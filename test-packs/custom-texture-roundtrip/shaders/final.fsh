#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D neutralTexture;
uniform float viewWidth;
uniform float viewHeight;
void main() {
    vec2 halfPixel = vec2(0.5 / viewWidth, 0.5 / viewHeight);
    vec2 coord = clamp(texcoord, halfPixel, vec2(1.0) - halfPixel);
    gl_FragColor = texture2D(colortex0, coord) * texture2D(neutralTexture, texcoord);
}
