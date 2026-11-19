uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
varying vec2 texcoord;
void main(){
    vec4 color=texture2D(colortex0,texcoord);
    // Only test pixels already classified as water with a backing opaque surface.
    bool nativeBehind=color.r>0.95&&abs(color.g-0.5)<0.03&&color.b<0.03;
    if(nativeBehind){
        float live=texture2D(depthtex0,texcoord).r;
        float opaque=texture2D(depthtex1,texcoord).r;
        color=opaque>live+0.000001?vec4(0.0,1.0,0.0,1.0):vec4(1.0,0.0,0.0,1.0);
    }
    gl_FragColor=color;
}
