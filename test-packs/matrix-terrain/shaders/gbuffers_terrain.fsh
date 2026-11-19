#version 120
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
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    bool invalid = matrixError > 0.005 || length(camera - cameraPosition) > 0.001;
    invalid = invalid || identityError(gbufferProjection * gbufferProjectionInverse) > 0.005;
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(albedo.rgb * vec3(0.4,1,0.5), 1);
}
