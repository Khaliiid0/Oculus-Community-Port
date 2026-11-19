// Authored local-only overlay inserted after the original cloud/fog code.
// Cyan=no cloud/fog-border threshold; red=cloud alpha; yellow=skyFade>=0.7;
// white=UV/reconstruction mismatch; magenta=clear depth; orange=invalid value.
ivec2 oculusPixel = ivec2(gl_FragCoord.xy);
if (oculusPixel.x >= 760 && oculusPixel.x < 900 && oculusPixel.y >= 310 && oculusPixel.y < 470) {
    vec2 oculusUv = (vec2(oculusPixel) + 0.5) / vec2(viewWidth, viewHeight);
    vec4 oculusReconstructed = gbufferProjectionInverse * vec4(oculusUv * 2.0 - 1.0, z0 * 2.0 - 1.0, 1.0);
    vec4 oculusValues = vec4(lViewPos, skyFade, clouds.a, z0);
    if (any(isnan(viewPos)) || any(isinf(viewPos)) || any(isnan(oculusValues)) || any(isinf(oculusValues)) || abs(oculusReconstructed.w) < 1e-8)
        color = vec4(1.0, 0.3, 0.0, 1.0);
    else if (z0 >= 1.0) color = vec4(1.0, 0.0, 1.0, 1.0);
    else if (distance(viewPos.xyz, oculusReconstructed.xyz / oculusReconstructed.w) > 0.05) color = vec4(1.0);
    else if (skyFade >= 0.7) color = vec4(1.0, 1.0, 0.0, 1.0);
    else if (clouds.a > 0.0001) color = vec4(1.0, 0.0, 0.0, 1.0);
    else color = vec4(0.0, 0.7, 1.0, 1.0);
}
