#include "helpers.glsl"
varying vec3 liveFromVertex;
void main(){
    liveFromVertex=activeMarker();
#ifdef NEGATIVE_VERTEX_MARKER
    liveFromVertex+=vec3(.125);
#endif
    gl_Position=ftransform();
}
