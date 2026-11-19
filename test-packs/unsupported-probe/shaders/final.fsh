#version 120
uniform sampler2D shadowtex0;
void main() {
    gl_FragColor = texture2D(shadowtex0, vec2(0.5));
}
