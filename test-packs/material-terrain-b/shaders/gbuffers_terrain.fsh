#version 120
/* DRAWBUFFERS:0 */
varying vec2 uv;
varying vec4 material;
uniform sampler2D texture;
void main() {
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    bool unknown = material.x == -1.0;
    bool invalid = !unknown && (material.x < 201.0 || material.x > 204.0);
    invalid = invalid || material.z != 0.0 || material.w != 1.0;
    invalid = invalid || (material.x == 204.0 ? material.y != 1.0 : material.y != 0.0);
    vec3 color = material.x == 201.0 ? vec3(0.1, 0.65, 0.9) : vec3(0.15, 0.7, 0.2);
    if (material.x == 204.0) color = vec3(0.1, 0.3, 1.0);
    if (unknown) color = vec3(0.35);
    gl_FragColor = invalid ? vec4(1, 0, 1, 1) : vec4(color * (0.65 + 0.35 * albedo.rgb), 1);
}
