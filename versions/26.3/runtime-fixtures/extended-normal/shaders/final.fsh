#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D colortex7;
uniform sampler2D shadowcolor0;
void main() {
    vec4 marker = texture2D(colortex7, texcoord);
    // Interior terrain pixels must contain the value written by the actual pack.
    bool invalid = marker.a > 0.999 && any(greaterThan(abs(marker.rgb - vec3(0.25, 0.5, 0.75)), vec3(0.002)));
    vec4 shadowMarker = texture2D(shadowcolor0, texcoord);
    invalid = invalid || (shadowMarker.a > 0.999 && any(greaterThan(abs(shadowMarker.rgb - vec3(0.125, 0.375, 0.625)), vec3(0.005))));
    gl_FragColor = invalid ? vec4(1.0, 0.0, 1.0, 1.0) : texture2D(colortex0, texcoord);
}
