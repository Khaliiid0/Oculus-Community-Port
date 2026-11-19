#include "values.glsl"
out vec4 stageFromVertex;
out float snowFromVertex;
void main(){
    stageFromVertex=vec4(rainProbe,skyProbe,frameProbe,effectProbe);
    snowFromVertex=snowProbe;
#ifdef NEGATIVE_SHARED_VERTEX
    stageFromVertex.x+=.125;
#endif
    gl_Position=ftransform();
}
