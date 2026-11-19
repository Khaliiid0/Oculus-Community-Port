#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
void main() {
    vec4 marker = texture2D(shadowcolor0, texcoord);
    bool invalid = marker.a > 0.999 && any(greaterThan(abs(marker.rgb - vec3(0.125, 0.375, 0.625)), vec3(0.005)));
    gl_FragColor = invalid ? vec4(1, 0, 1, 1) : texture2D(colortex0, texcoord);
}
