#version 330 compatibility
uniform float cloudHeight;
out float cloudFromVertex;
void main(){
    cloudFromVertex=cloudHeight;
#ifdef NEGATIVE_CLOUD_VERTEX
    cloudFromVertex+=16.;
#endif
    gl_Position=ftransform();
}
