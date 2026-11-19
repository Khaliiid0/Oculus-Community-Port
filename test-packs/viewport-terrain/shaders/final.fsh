#version 120
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
void main() {
    vec2 size = vec2(textureSize(colortex0, 0));
    vec3 expected = vec3(size, size.x / size.y) / vec3(8192,8192,8);
    vec4 marker = texture2D(shadowcolor0, texcoord);
    bool invalid = marker.a > 0.999 && any(greaterThan(abs(marker.rgb - expected), vec3(0.005)));
    gl_FragColor = invalid ? vec4(1,0,1,1) : texture2D(colortex0, texcoord);
}
