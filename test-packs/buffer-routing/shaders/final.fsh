#version 120
#include "debug.glsl"
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D composite;
void main() {
    vec4 marker = texture2D(composite, texcoord);
    bool routed = distance(marker.rgb, vec3(0.125, 0.75, 0.25)) < 0.015;
    vec4 indicator = routed ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
#if OCULUS_ROUTING_OVERLAY
    gl_FragColor = texcoord.x < 0.15 ? indicator : texture2D(colortex0, texcoord);
#else
    gl_FragColor = texture2D(colortex0, texcoord);
#endif
}
