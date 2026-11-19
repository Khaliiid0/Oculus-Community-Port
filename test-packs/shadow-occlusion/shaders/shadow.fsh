#version 120
const int shadowMapResolution = 1024;
const float shadowDistance = 64.0;
varying vec3 worldPosition;
void main() {
    // Ignore other world geometry: only the deliberately constructed fixture counts.
    if (worldPosition.x < 55.99 || worldPosition.x > 73.01 ||
        worldPosition.y < 199.99 || worldPosition.y > 211.01 ||
        worldPosition.z < -40.01 || worldPosition.z > -22.99) discard;
    float encoded = floor(gl_FragCoord.z * 65535.0 + 0.5);
    gl_FragColor = vec4(floor(encoded / 256.0) / 255.0, mod(encoded, 256.0) / 255.0, 0.75, 1.0);
}
