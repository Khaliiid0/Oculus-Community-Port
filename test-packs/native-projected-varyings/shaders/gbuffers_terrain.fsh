#version 120
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth;
uniform float viewHeight;
varying vec4 namedClip;
varying vec4 builtinClip;
varying vec3 viewPosition;
const float expectedOffset = 0.0;
void main() {
    vec3 fragmentNdc = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), gl_FragCoord.z) * 2.0 - 1.0;
    vec3 namedNdc = namedClip.xyz / namedClip.w;
    vec3 builtinNdc = builtinClip.xyz / builtinClip.w;
    vec4 recovered = gbufferProjectionInverse * vec4(fragmentNdc, 1.0);
    float reconstructionError = length(recovered.xyz / recovered.w - viewPosition) / max(length(viewPosition), 1.0);
    bool valid = length(namedNdc - fragmentNdc - vec3(0.0, 0.0, expectedOffset)) < 0.0001;
    valid = valid && length(builtinNdc - namedNdc) < 0.0001 && reconstructionError < 0.002;
    gl_FragColor = valid ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
