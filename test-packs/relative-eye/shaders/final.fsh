#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;
void main() {
    ivec2 size = textureSize(shadowcolor0, 0);
    ivec2 pixel = clamp(ivec2(texcoord * vec2(size)), ivec2(0), size - ivec2(1));
    vec4 marker = texelFetch(shadowcolor0, pixel, 0);
    bool invalid = marker.a > 0.999 && (marker.r > 0.5 || marker.g < 0.5 || marker.b > 0.5);
    gl_FragColor = invalid ? vec4(1,0,1,1) : texture2D(colortex0, texcoord);
}
