#version 120
/* DRAWBUFFERS:0 */
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D colortex7;
uniform sampler2D colortex8;
uniform sampler2D colortex9;
void main() {
    vec4 postValue = texture2D(colortex7, texcoord);
    vec4 original = texture2D(colortex9, texcoord);
    vec4 unchanged = texture2D(colortex8, texcoord);
    vec3 difference = abs(postValue.rgb - original.rgb - 1.0);
    bool valid = original.a == 1.0 && postValue.a == 2.0 && unchanged.a == 1.0
        && dot(abs(unchanged.rgb), vec3(1.0)) < 0.001
        && max(max(difference.r, difference.g), difference.b) < 0.002;
    gl_FragColor = valid ? texture2D(colortex0, texcoord) : vec4(1.0, 0.0, 1.0, 1.0);
}
