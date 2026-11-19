#version 120
/* DRAWBUFFERS:0 */
uniform sampler2D texture;
uniform int frameCounter;
uniform float frameTime, frameTimeCounter;
varying vec2 uv;
varying vec3 animation;
void main() {
    vec4 albedo = texture2D(texture, uv);
    if (albedo.a < 0.1) discard;
    bool invalid = frameCounter < 0 || frameCounter >= 720720 || frameTime < 0.0;
    invalid = invalid || frameTimeCounter < 0.0 || frameTimeCounter >= 3600.0;
    invalid = invalid || abs(animation.x - float(frameCounter)) > 0.25;
    invalid = invalid || abs(animation.y - frameTime) > 0.001 || abs(animation.z - frameTimeCounter) > 0.001;
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(albedo.rgb * vec3(0.4,1,0.5), 1);
}
