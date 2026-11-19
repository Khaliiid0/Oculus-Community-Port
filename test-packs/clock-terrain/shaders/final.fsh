#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform int worldTime;
uniform int worldDay;
const int expectedTime = 6000;
const int expectedDay = 257;
void main() {
    ivec2 size = textureSize(shadowcolor0, 0);
    ivec2 pixel = clamp(ivec2(texcoord * vec2(size)), ivec2(0), size - ivec2(1));
    vec4 marker = texelFetch(shadowcolor0, pixel, 0);
    int nativeTime = int(round(marker.r * 255.0)) + 256 * int(round(marker.g * 255.0));
    int nativeDayByte = int(round(marker.b * 255.0));
    bool invalid = marker.a > 0.999 && (nativeTime != worldTime || nativeDayByte != (worldDay % 256 + 256) % 256);
    invalid = invalid || (expectedTime >= 0 && worldTime != expectedTime);
    invalid = invalid || (expectedDay >= 0 && worldDay != expectedDay);
    gl_FragColor = invalid ? vec4(1,0,1,1) : texture2D(colortex0, texcoord);
}
