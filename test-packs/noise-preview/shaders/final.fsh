#version 120
const int noiseTextureResolution = 128;
varying vec2 texcoord;
uniform sampler2D colortex0;
uniform sampler2D noisetex;
void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec3 grain = texture2D(noisetex, gl_FragCoord.xy / float(noiseTextureResolution)).rgb - vec3(0.5);
    gl_FragColor = vec4(clamp(scene.rgb + grain * 0.008, 0.0, 1.0), scene.a);
}
