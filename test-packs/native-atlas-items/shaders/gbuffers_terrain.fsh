#version 330 compatibility
/* DRAWBUFFERS:04 */
uniform ivec2 atlasSize;
uniform int heldItemId,heldItemId2;
uniform sampler2D gtexture;
in vec4 inputsFromVertex;
float code(int id){return id==40008?.125:id==44002?.375:id==44010?.625:id==77?.875:0.;}
void main(){
    vec4 expected=vec4(vec2(atlasSize),float(heldItemId),float(heldItemId2));
    bool bad=any(greaterThan(abs(inputsFromVertex-expected),vec4(.1)))
        ||any(notEqual(atlasSize,textureSize(gtexture,0)))||any(lessThanEqual(atlasSize,ivec2(0)));
    vec4 value=vec4(bad?vec3(1,0,1):vec3(code(heldItemId),code(heldItemId2),.75),1);
    gl_FragData[0]=value;gl_FragData[1]=value;
}
