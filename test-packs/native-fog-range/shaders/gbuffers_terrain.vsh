#version 330 compatibility
out vec4 fogFromVertex;
out vec3 fogColorFromVertex;
void main(){
    fogFromVertex=vec4(gl_Fog.start,gl_Fog.end,gl_Fog.scale,gl_Fog.color.a);
    fogColorFromVertex=gl_Fog.color.rgb;
#ifdef NEGATIVE_FOG_VERTEX
    fogFromVertex.x+=16.;
#endif
    gl_Position=ftransform();
}
