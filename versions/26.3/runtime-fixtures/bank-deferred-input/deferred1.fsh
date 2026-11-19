#version 130
// Authored LOCAL-ONLY diagnostic replacement, not a shader-pack fix.
// Bank: orange=invalid, magenta=live clear, blue=snapshot clear,
// white=depth disagreement, yellow=wrong far, red=distance>=128, cyan=near.
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform float viewWidth;
uniform float viewHeight;
uniform float far;
uniform float cloudHeight;
uniform vec3 cameraPosition;
uniform ivec3 cameraPositionInt;
uniform vec3 cameraPositionFract;
bool invalid(vec4 value) { return any(isnan(value)) || any(isinf(value)); }
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 color = texelFetch(colortex0, pixel, 0);
    float liveDepth = texelFetch(depthtex0, pixel, 0).r;
    float opaqueDepth = texelFetch(depthtex1, pixel, 0).r;
    vec2 uv = (vec2(pixel) + 0.5) / vec2(viewWidth, viewHeight);
    vec4 view = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, liveDepth * 2.0 - 1.0, 1.0);
    vec4 player = gbufferModelViewInverse * vec4(view.xyz / view.w, 1.0);
    float distanceToGeometry = length(view.xyz / view.w);
    if (pixel.x >= 760 && pixel.x < 900 && pixel.y >= 310 && pixel.y < 470) {
        if (invalid(view) || invalid(player) || invalid(vec4(liveDepth, opaqueDepth, far, distanceToGeometry)) || abs(view.w) < 1e-8)
            color = vec4(1.0, 0.3, 0.0, 1.0);
        else if (liveDepth >= 1.0) color = vec4(1.0, 0.0, 1.0, 1.0);
        else if (opaqueDepth >= 1.0) color = vec4(0.0, 0.0, 1.0, 1.0);
        else if (abs(liveDepth - opaqueDepth) > 1e-6) color = vec4(1.0);
        else if (abs(far - 128.0) > 0.001) color = vec4(1.0, 1.0, 0.0, 1.0);
        else if (distanceToGeometry >= 128.0) color = vec4(1.0, 0.0, 0.0, 1.0);
        else color = vec4(0.0, 0.7, 1.0, 1.0);
    }
    // Three independent environment controls (left to right) at bottom left:
    // cloud height=192.33 (26.3 default), recorded eye, integer/fraction identity.
    if (pixel.y >= 20 && pixel.y < 60 && pixel.x >= 20 && pixel.x < 140) {
        int panel = (pixel.x - 20) / 40;
        bool ok = panel == 0 ? !invalid(vec4(cloudHeight)) && abs(cloudHeight - 192.33) < 0.01
            : panel == 1 ? !invalid(vec4(cameraPosition, 1.0)) && distance(cameraPosition, vec3(2120.5, 66.62, 522.5)) < 0.1
            : !invalid(vec4(cameraPositionFract, 1.0)) && distance(vec3(cameraPositionInt) + cameraPositionFract, cameraPosition) < 0.001;
        color = ok ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
    }
    // Exact IEEE-754 bits, least significant bit at the left. Each row has
    // 32 four-pixel cells; six-pixel rows start at GL y=70. Decode only after
    // known far and camera-Y controls match. No shaderpack GLSL is included.
    if (pixel.x >= 20 && pixel.x < 148 && pixel.y >= 70 && pixel.y < 118) {
        int row = (pixel.y - 70) / 6;
        int bit = (pixel.x - 20) / 4;
        float sampledDepth = texelFetch(depthtex0, ivec2(825, 395), 0).r;
        vec2 sampledUv = (vec2(825, 395) + 0.5) / vec2(viewWidth, viewHeight);
        vec4 sampledView = gbufferProjectionInverse * vec4(sampledUv * 2.0 - 1.0, sampledDepth * 2.0 - 1.0, 1.0);
        vec3 reconstructed = sampledView.xyz / sampledView.w;
        float value = row == 0 ? far : row == 1 ? cameraPosition.y
            : row == 2 ? cloudHeight : row == 3 ? sampledDepth
            : row == 4 ? texelFetch(depthtex1, ivec2(825, 395), 0).r
            : row == 5 ? reconstructed.x : row == 6 ? reconstructed.y : reconstructed.z;
        float on = float((floatBitsToUint(value) >> uint(bit)) & 1u);
        color = vec4(vec3(on), 1.0);
    }
    /* DRAWBUFFERS:054 */
    gl_FragData[0] = vec4(color.rgb, 1.0);
    gl_FragData[1] = vec4(0.0, 0.0, 0.0, 1.0);
    gl_FragData[2] = vec4(texelFetch(colortex4, pixel, 0).rgb, 0.0);
}
