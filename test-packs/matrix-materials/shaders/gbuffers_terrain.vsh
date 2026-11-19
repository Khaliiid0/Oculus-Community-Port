#version 120
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;
attribute vec4 mc_Entity;
attribute vec4 at_tangent;
varying vec2 uv;
varying float matrixError;
varying vec3 camera;
varying float materialId;
varying vec2 meshError;
float identityError(mat4 m) {
    return length(m[0] - vec4(1,0,0,0)) + length(m[1] - vec4(0,1,0,0)) + length(m[2] - vec4(0,0,1,0)) + length(m[3] - vec4(0,0,0,1));
}
void main() {
    uv = gl_MultiTexCoord0.xy;
    camera = cameraPosition;
    materialId = mc_Entity.x;
    vec4 expected = ftransform();
    vec4 actual = gbufferProjection * gbufferModelView * gl_Vertex;
    matrixError = length(actual - expected) / max(length(expected), 1.0);
    matrixError += identityError(gbufferProjection * gbufferProjectionInverse);
    matrixError += identityError(gbufferModelView * gbufferModelViewInverse);
    matrixError += identityError(shadowProjection * shadowProjectionInverse);
    matrixError += identityError(shadowModelView * shadowModelViewInverse);
    meshError = vec2(abs(length(gl_Normal) - 1.0), abs(dot(gl_Normal, at_tangent.xyz)));
    bool materialValid = mc_Entity.y == 1.0 ? mc_Entity.x == 302.0 : (mc_Entity.x == -1.0 || mc_Entity.x == 301.0);
    if (!materialValid) matrixError += 1.0;
    gl_Position = actual;
}
