#version 330 compatibility
uniform vec3 skyColor;
out vec3 skyFromVertex;
void main(){
    skyFromVertex=skyColor;
#ifdef NEGATIVE_SKY_VERTEX
    skyFromVertex.r+=.125;
#endif
    gl_Position=ftransform();
}
