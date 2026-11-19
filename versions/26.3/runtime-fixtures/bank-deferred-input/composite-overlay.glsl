// LOCAL diagnostic only: RGB flags for a contribution at the original pixel.
// R=refraction changes colour; G=reflection changes colour; B=volumetric RGB.
ivec2 oculusCompositePixel = ivec2(gl_FragCoord.xy);
if (oculusCompositePixel.x >= 760 && oculusCompositePixel.x < 900 && oculusCompositePixel.y >= 310 && oculusCompositePixel.y < 470) {
    color = vec3(float(length(oculusAfterRefraction - oculusBeforeRefraction) > 0.0001),
        float(length(oculusAfterReflection - oculusAfterRefraction) > 0.0001),
        float(length(volumetricEffect.rgb) > 0.0001));
}
