#version 120
#extension GL_ARB_shader_texture_lod : enable
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
uniform sampler2D texture;
varying vec2 uv;
varying float matrixError;
void main() {
    vec4 albedo = texture2DLod(texture, uv, 0.0);
    vec4 projected = texture2DProjLod(texture, vec3(uv * 2.0, 2.0), 0.0);
    vec4 gradient = texture2DGradARB(texture, uv, vec2(0.0), vec2(0.0));
    vec4 projectedGradient = texture2DProjGradARB(texture, vec3(uv * 2.0, 2.0), vec2(0.0), vec2(0.0));
    if (albedo.a < 0.1) discard;
    bool invalid = matrixError > 0.005 || length(albedo - projected) > 0.005 || length(albedo - gradient) > 0.005 || length(albedo - projectedGradient) > 0.005;
    vec4 total = albedo + projected + gradient + projectedGradient;
    invalid = invalid || any(isnan(total)) || any(isinf(total));
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(0.125,0.375,0.625,1);
}
