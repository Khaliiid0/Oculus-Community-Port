#version 330 compatibility
uniform ivec2 atlasSize;
uniform int heldItemId,heldItemId2;
out vec4 inputsFromVertex;
void main(){
    inputsFromVertex=vec4(vec2(atlasSize),float(heldItemId),float(heldItemId2));
#ifdef NEGATIVE_ITEM_VERTEX
    inputsFromVertex.z+=1.;
#endif
    gl_Position=ftransform();
}
