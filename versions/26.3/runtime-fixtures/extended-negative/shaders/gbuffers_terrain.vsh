#version 120
attribute vec4 mc_Entity;
attribute vec4 at_tangent;
attribute vec2 mc_midTexCoord;
varying float meshBad;
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    tint = gl_Color;
    bool invalidMesh = length(gl_Normal) < .9 || length(at_tangent.xyz) < .9
        || abs(dot(normalize(gl_Normal), normalize(at_tangent.xyz))) > .05
        || abs(at_tangent.w) < .9
        || any(lessThan(mc_midTexCoord, vec2(0.))) || any(greaterThan(mc_midTexCoord, vec2(1.)))
        || mc_Entity.x < 999.5 || mc_Entity.x > 19.5 || abs(mc_Entity.y) > .01;
    meshBad = invalidMesh ? 1. : 0.;
    gl_Position = ftransform();
}
