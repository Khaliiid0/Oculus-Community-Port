#version 120
varying vec2 texcoord;
uniform sampler2D shadowcolor0;
void main() {
    // Explicit development probe: display the shadow map, not an altered player world.
    gl_FragColor = texture2D(shadowcolor0, texcoord);
}
