#version 330 compatibility
uniform float darknessLightFactor,darknessFactor;
out vec2 darknessFromVertex;
void main(){
    darknessFromVertex=vec2(darknessLightFactor,darknessFactor);
#ifdef NEGATIVE_DARKNESS_VERTEX
    darknessFromVertex.x+=.125;
#endif
    gl_Position=ftransform();
}
