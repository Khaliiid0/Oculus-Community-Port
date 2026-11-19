uniform sampler2D specular;
uniform ivec2 atlasSize;
in vec2 materialUv;
bool nearColor(vec4 a,vec4 b){return all(lessThan(abs(a-b),vec4(.006)));}
vec4 materialMarker(){
    vec4 value=texture2DLod(specular,materialUv,0.0);
    vec4 mip=texture2DLod(specular,materialUv,1000.0);
#ifdef NEGATIVE_SPECULAR
    value.r+=.125;
#endif
    bool mapped=!nearColor(value,vec4(0));
    bool bad=mapped
        ? !(nearColor(value,vec4(32,64,96,128)/255.)||nearColor(value,vec4(64,96,128,160)/255.)
            ||nearColor(value,vec4(96,128,160,192)/255.)||nearColor(value,vec4(128,160,192,224)/255.))
            ||!nearColor(mip,vec4(80,112,144,176)/255.)
            ||any(notEqual(textureSize(specular,0),atlasSize))
            ||any(notEqual(textureSize(specular,4),atlasSize/16))
        : !nearColor(mip,vec4(0));
    return vec4(bad?.75:.25,mapped?.75:.25,.125,.375);
}
