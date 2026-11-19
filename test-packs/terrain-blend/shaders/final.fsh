uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex8;
varying vec2 texcoord;
void main() {
    vec4 scene=texture2D(colortex0,texcoord);
    vec4 first=texture2D(colortex4,texcoord);
    vec4 last=texture2D(colortex8,texcoord);
    if (first.r>0.01 || last.g>0.01) {
        bool correct=all(lessThan(abs(first-vec4(1.0,0.5,0.0,0.25)),vec4(0.012)))
            && all(lessThan(abs(last-vec4(0.0,1.0,0.25,0.25)),vec4(0.012)))
            && all(lessThan(abs(scene-vec4(0.5,0.0,0.5,1.0)),vec4(0.012)));
        gl_FragColor=correct?vec4(0.0,1.0,0.0,1.0):vec4(1.0,0.0,1.0,1.0);
    } else gl_FragColor=scene;
}
