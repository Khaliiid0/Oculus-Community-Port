#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform int frameCounter, worldTime;
const float expectedDivisor = 24000.0;
varying vec2 texcoord;
void main() {
    ivec2 size = textureSize(shadowcolor0, 0);
    ivec2 pixel = clamp(ivec2(texcoord * vec2(size)), ivec2(0), size - ivec2(1));
    vec4 marker = texelFetch(shadowcolor0, pixel, 0);
    bool invalid = int(round(marker.r * 255.0)) != frameCounter % 8;
    invalid = invalid || int(round(marker.g * 255.0)) != frameCounter % 2;
    invalid = invalid || abs(marker.b - float(worldTime) / expectedDivisor) > 0.006;
    gl_FragColor = marker.a > 0.999 && invalid ? vec4(1,0,1,1) : texture2D(colortex0, texcoord);
}
