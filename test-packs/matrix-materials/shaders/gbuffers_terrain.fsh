#version 120
/* DRAWBUFFERS:0 */
uniform vec3 cameraPosition;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform sampler2D texture;
varying vec2 uv;
varying float matrixError;
varying vec3 camera;
varying float materialId;
varying vec2 meshError;
float identityError(mat4 m) {
    return length(m[0] - vec4(1,0,0,0)) + length(m[1] - vec4(0,1,0,0)) + length(m[2] - vec4(0,0,1,0)) + length(m[3] - vec4(0,0,0,1));
}
void main() {
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    bool invalid = matrixError > 0.005 || length(camera - cameraPosition) > 0.001;
    invalid = invalid || identityError(gbufferProjection * gbufferProjectionInverse) > 0.005;
    // SNORM8 rounding: vector error <= sqrt(3)/254; dot error <= 2e + e*e.
    // Keep the float-matrix test independent instead of summing different precisions.
    invalid = invalid || meshError.x > 0.007 || meshError.y > 0.014;
    vec3 marker = materialId == 301.0 ? vec3(0.2,0.8,0.3) : (materialId == 302.0 ? vec3(0.1,0.3,1.0) : vec3(0.4));
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(marker * (0.65 + 0.35 * albedo.rgb), 1);
}
