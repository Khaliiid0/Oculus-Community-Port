#version 120
/* DRAWBUFFERS:0 */
varying vec2 uv;
varying vec3 normal;
varying vec4 tangent;
varying vec2 midUv;
uniform sampler2D texture;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    bool invalid = abs(length(normal) - 1.0) > 0.02 || abs(length(tangent.xyz) - 1.0) > 0.02;
    invalid = invalid || abs(dot(normal, tangent.xyz)) > 0.03 || abs(abs(tangent.w) - 1.0) > 0.01;
    invalid = invalid || any(lessThan(midUv, vec2(0.0))) || any(greaterThan(midUv, vec2(1.0)));
    // Face direction colors plus a quad-UV quadrant pattern exercise every extended input.
    float quadrant = (uv.x < midUv.x) == (uv.y < midUv.y) ? 1.0 : 0.75;
    gl_FragColor = invalid ? vec4(1.0, 0.0, 1.0, 1.0) : vec4((normal * 0.5 + 0.5) * quadrant, 1.0);
}
