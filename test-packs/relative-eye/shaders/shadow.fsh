#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
uniform sampler2D texture;
uniform vec3 relativeEyePosition;
uniform mat4 gbufferModelView;
varying vec2 uv;
varying vec3 eyeOffset;
// Set to 4.0 for an unobstructed third-person test, 0.0 for first person.
const float expectedDistance = 0.0;
bool invalidEye() {
    vec3 viewOffset = (gbufferModelView * vec4(relativeEyePosition, 0.0)).xyz;
    return any(greaterThan(abs(eyeOffset - relativeEyePosition), vec3(0.0001)))
        || abs(viewOffset.z - expectedDistance) > 0.06
        || length(viewOffset.xy) > 0.06;
}

void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    gl_FragColor = invalidEye() ? vec4(1,0,1,1) : vec4(0,1,0,1);
}
