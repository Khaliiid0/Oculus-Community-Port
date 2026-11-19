#version 120
#extension GL_ARB_shader_texture_lod : require
/* DRAWBUFFERS:0 */
uniform vec3 cameraPosition;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform sampler2D texture;
varying vec2 uv;
varying float matrixError;
varying vec3 camera;
float identityError(mat4 m) {
    return length(m[0] - vec4(1,0,0,0)) + length(m[1] - vec4(0,1,0,0)) + length(m[2] - vec4(0,0,1,0)) + length(m[3] - vec4(0,0,0,1));
}
void main() {
    vec4 albedo = texture2DLod(texture, uv, 0.0);
    vec4 projected = texture2DProjLod(texture, vec3(uv * 2.0, 2.0), 0.0);
    vec4 gradient = texture2DGradARB(texture, uv, vec2(0.0), vec2(0.0));
    vec4 projectedGradient = texture2DProjGradARB(texture, vec3(uv * 2.0, 2.0), vec2(0.0), vec2(0.0));
    if (albedo.a < 0.1) discard;
    bool invalid = matrixError > 0.005 || length(camera - cameraPosition) > 0.001;
    invalid = invalid || identityError(gbufferProjection * gbufferProjectionInverse) > 0.005;
    invalid = invalid || length(albedo - projected) > 0.005 || length(albedo - gradient) > 0.005 || length(albedo - projectedGradient) > 0.005;
    vec4 total = albedo + projected + gradient + projectedGradient;
    invalid = invalid || any(isnan(total)) || any(isinf(total));
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(albedo.rgb * vec3(0.4,1,0.5), 1);
}
