#version 120
/* DRAWBUFFERS:0 */
uniform int worldTime;
uniform int worldDay;
const int expectedNativeDay = 257;
uniform sampler2D texture;
varying vec2 uv;
varying float vertexTime;
varying float vertexDay;
void main() {
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    bool invalid = worldTime < 0 || worldTime >= 24000 || abs(vertexTime - float(worldTime)) > 0.01;
    invalid = invalid || abs(vertexDay - float(worldDay)) > 0.01;
    invalid = invalid || (expectedNativeDay >= 0 && worldDay != expectedNativeDay);
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(albedo.rgb * vec3(0.4,1,0.5), 1);
}
