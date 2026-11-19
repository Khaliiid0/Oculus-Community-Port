#version 120
/* DRAWBUFFERS:0 */
uniform float viewWidth, viewHeight, aspectRatio;
uniform sampler2D texture;
varying vec2 uv;
varying vec3 viewport;
void main() {
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    vec3 current = vec3(viewWidth, viewHeight, aspectRatio);
    bool invalid = any(isnan(current)) || any(isinf(current));
    invalid = invalid || viewWidth < 1.0 || viewHeight < 1.0;
    invalid = invalid || length(current - viewport) > 0.005;
    invalid = invalid || abs(aspectRatio - viewWidth / viewHeight) > 0.0001;
    invalid = invalid || gl_FragCoord.x > viewWidth || gl_FragCoord.y > viewHeight;
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(albedo.rgb * vec3(0.4,1,0.5), 1);
}
