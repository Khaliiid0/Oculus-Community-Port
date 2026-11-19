#version 120
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor0;
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;
uniform mat4 shadowProjectionInverse;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;
uniform float sunAngle;
varying vec2 texcoord;
const float expectedOffset = 0.0;

float intersectBox(vec3 origin, vec3 direction, vec3 minimum, vec3 maximum) {
    // Near-zero components need an explicit parallel slab test.
    float entry = 0.0;
    float exit = 1.0;
    for (int axis = 0; axis < 3; axis++) {
        if (abs(direction[axis]) < 0.00001) {
            if (origin[axis] < minimum[axis] || origin[axis] > maximum[axis]) return 2.0;
        } else {
            float a = (minimum[axis] - origin[axis]) / direction[axis];
            float b = (maximum[axis] - origin[axis]) / direction[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
        }
    }
    return entry <= exit ? entry : 2.0;
}

void main() {
    vec4 center = shadowProjection * shadowModelView * vec4(vec3(64.5, 204.0, -31.5) - cameraPosition, 1.0);
    vec2 requested = center.xy / center.w * 0.5 + 0.5 + (texcoord - 0.5) * 0.26;
    ivec2 pixel = ivec2(floor(requested * 1024.0));
    vec2 uv = (vec2(pixel) + 0.5) / 1024.0;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
        gl_FragColor = vec4(1.0, 0.5, 0.0, 1.0);
        return;
    }
    // Reconstruct a light ray through the exact sampled texel center. The expected
    // depth comes from the two known boxes, independently of rasterized markers.
    vec4 nearPoint = shadowModelViewInverse * shadowProjectionInverse * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 farPoint = shadowModelViewInverse * shadowProjectionInverse * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 origin = nearPoint.xyz / nearPoint.w + cameraPosition;
    vec3 direction = farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w;
    float floorHit = intersectBox(origin, direction, vec3(56.0, 200.0, -40.0), vec3(73.0, 202.0, -23.0));
    float pillarHit = intersectBox(origin, direction, vec3(63.0, 202.0, -33.0), vec3(66.0, 211.0, -30.0));
    float hit = min(floorHit, pillarHit);
    float live = texelFetch(shadowtex0, pixel, 0).r;
    float opaque = texelFetch(shadowtex1, pixel, 0).r;
    vec4 marker = texelFetch(shadowcolor0, pixel, 0);
    if (hit > 1.0) {
        bool empty = abs(live - 1.0) < 0.00002 && abs(opaque - 1.0) < 0.00002;
        gl_FragColor = empty ? vec4(0.15, 0.15, 0.15, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
        return;
    }
    vec4 expectedClip = shadowProjection * shadowModelView * vec4(origin + hit * direction - cameraPosition, 1.0);
    float expected = expectedClip.z / expectedClip.w * 0.5 + 0.5 + expectedOffset;
    bool depthValid = abs(live - expected) < 0.00003 && abs(opaque - expected) < 0.00003;
    float markerDepth = (floor(marker.r * 255.0 + 0.5) * 256.0 + floor(marker.g * 255.0 + 0.5)) / 65535.0;
    bool markerValid = marker.a > 0.999 && abs(markerDepth - live) < 0.00002;
    float angle = sunAngle * 6.28318530718 - 1.57079632679;
    vec3 sunDirection = vec3(-sin(angle), cos(angle), 0.0);
    vec3 lightInView = (shadowModelView * vec4(sunDirection, 0.0)).xyz;
    if (sunDirection.y > 0.01 && length(lightInView - vec3(0.0, 0.0, 1.0)) > 0.0001) {
        gl_FragColor = vec4(1.0, 1.0, 0.0, 1.0);
        return;
    }
    vec3 passing = pillarHit < floorHit ? vec3(0.0, 0.7, 1.0) : vec3(0.0, 1.0, 0.0);
    gl_FragColor = depthValid && markerValid ? vec4(passing, 1.0) : vec4(1.0, 0.0, 1.0, 1.0);
}
