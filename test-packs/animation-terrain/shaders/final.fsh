#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform int frameCounter;
uniform float frameTime, frameTimeCounter;
void main() {
    ivec2 size = textureSize(shadowcolor0, 0);
    ivec2 pixel = clamp(ivec2(texcoord * vec2(size)), ivec2(0), size - ivec2(1));
    vec4 marker = texelFetch(shadowcolor0, pixel, 0);
    ivec3 bytes = ivec3(round(marker.rgb * 255.0));
    int nativeCounter = bytes.r + 256 * bytes.g + 65536 * bytes.b;
    bool invalid = marker.a > 0.999 && nativeCounter != frameCounter;
    invalid = invalid || frameTime < 0.0 || frameTimeCounter < 0.0 || frameTimeCounter >= 3600.0;
    gl_FragColor = invalid ? vec4(1,0,1,1) : texture2D(colortex0, texcoord);
}
