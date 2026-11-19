#version 120
uniform sampler2D colortex15;
varying float vertexSample;
void main(){
    vertexSample=texture2D(colortex15,vec2(0.5)).r;
    gl_Position=ftransform();
}
