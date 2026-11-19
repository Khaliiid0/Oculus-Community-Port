#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
uniform sampler2D texture;
uniform int frameCounter, worldTime;
uniform float framemod8, framemod2, timeAngle;
const float expectedDivisor = 24000.0;
varying vec2 uv;
varying vec3 customClock;
void main() {
    if (texture2D(texture, uv).a < 0.1) discard;
    vec3 expected = vec3(float(frameCounter % 8), float(frameCounter % 2), float(worldTime) / expectedDivisor);
    bool invalid = any(greaterThan(abs(customClock - expected), vec3(0.0001)));
    invalid = invalid || any(greaterThan(abs(vec3(framemod8, framemod2, timeAngle) - expected), vec3(0.0001)));
    gl_FragColor = invalid ? vec4(1,0,1,1) : vec4(framemod8 / 255.0, framemod2 / 255.0, timeAngle, 1);
}
