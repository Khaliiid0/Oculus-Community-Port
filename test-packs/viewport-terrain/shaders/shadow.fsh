#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
uniform float viewWidth, viewHeight, aspectRatio;
uniform sampler2D texture;
varying vec2 uv;
varying vec3 viewport;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    vec3 current = vec3(viewWidth, viewHeight, aspectRatio);
    bool invalid = any(isnan(current)) || any(isinf(current));
    invalid = invalid || viewWidth < 1.0 || viewHeight < 1.0;
    invalid = invalid || length(current - viewport) > 0.005;
    invalid = invalid || abs(aspectRatio - viewWidth / viewHeight) > 0.0001;
    // RGBA8 marker tolerates quantization; intended test resolutions are below 8192.
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(current / vec3(8192,8192,8), 1);
}
