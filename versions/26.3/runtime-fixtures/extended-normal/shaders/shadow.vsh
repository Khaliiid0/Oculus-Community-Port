#version 120
attribute vec4 mc_Entity;
attribute vec4 at_tangent;
attribute vec2 mc_midTexCoord;
varying float shadowMeshBad;
const int shadowMapResolution = 512;
const float shadowDistance = 64.0;
varying vec2 uv;
void main() {
    uv = gl_MultiTexCoord0.xy;
    bool invalidMesh = length(gl_Normal) < .9 || length(at_tangent.xyz) < .9
        || abs(dot(normalize(gl_Normal), normalize(at_tangent.xyz))) > .05
        || abs(at_tangent.w) < .9
        || any(lessThan(mc_midTexCoord, vec2(0.))) || any(greaterThan(mc_midTexCoord, vec2(1.)))
        || mc_Entity.x < 16.5 || mc_Entity.x > 19.5 || abs(mc_Entity.y) > .01;
    shadowMeshBad = invalidMesh ? 1. : 0.;
    gl_Position = ftransform();
}
