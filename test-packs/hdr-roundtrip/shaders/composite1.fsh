#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex7;
uniform sampler2D colortex8;
uniform sampler2D colortex9;
uniform sampler2D colortex10;
uniform sampler2D colortex0;
void main() {
    vec4 encoded = texture2D(colortex7, texcoord);
    vec4 signedProbe = texture2D(colortex8, texcoord);
    vec4 rgbFloatProbe = texture2D(colortex9, texcoord);
    vec4 rgbProbe = texture2D(colortex10, texcoord);
    vec3 restored = (encoded.rgb + 1.0) * 0.25;
    vec3 difference = abs(restored - texture2D(colortex0, texcoord).rgb);
    bool passed = abs(encoded.a - 2.0) < 0.01 && abs(signedProbe.r + 0.5) < 0.01
        && signedProbe.a == 1.0 && rgbFloatProbe.a == 1.0 && rgbProbe.a == 1.0
        && abs(rgbFloatProbe.r + 2.0) < 0.01 && abs(rgbFloatProbe.g - 4.0) < 0.01
        && abs(rgbProbe.r - 0.25) < 0.01 && abs(rgbProbe.b - 0.75) < 0.01
        && max(max(difference.r, difference.g), difference.b) < 0.003;
    gl_FragColor = passed ? vec4(restored, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
