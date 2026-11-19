#version 120
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
void main() {
    uv=(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy;
    lm=(gl_TextureMatrix[1]*gl_MultiTexCoord1).xy;
    tint=gl_Color;
    gl_Position=ftransform();
}
