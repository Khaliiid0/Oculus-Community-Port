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
varying vec2 uv;
varying float matrixError;
float identityError(mat4 m) {
    return length(m[0] - vec4(1,0,0,0)) + length(m[1] - vec4(0,1,0,0)) + length(m[2] - vec4(0,0,1,0)) + length(m[3] - vec4(0,0,0,1));
}
void main() {
    uv = gl_MultiTexCoord0.xy;
    vec4 actual = shadowProjection * shadowModelView * gl_Vertex;
    vec4 separate = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
    vec4 expected = ftransform();
    matrixError = length(actual - expected) + length(separate - expected);
    matrixError += identityError(shadowProjection * shadowProjectionInverse);
    matrixError += identityError(shadowModelView * shadowModelViewInverse);
    matrixError += identityError(gbufferProjection * gbufferProjectionInverse);
    matrixError += identityError(gbufferModelView * gbufferModelViewInverse);
    matrixError += length(gl_NormalMatrix * gl_Normal - mat3(transpose(shadowModelViewInverse)) * gl_Normal);
    if (abs(length(gl_NormalMatrix * gl_Normal) - 1.0) > 0.007) matrixError += 1.0;
    if (mc_Entity.x != -1.0 && mc_Entity.x != 301.0 && mc_Entity.x != 302.0) matrixError += 1.0;
    gl_Position = actual;
}
