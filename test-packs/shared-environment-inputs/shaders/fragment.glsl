#include "values.glsl"
in vec4 stageFromVertex;
in float snowFromVertex;
bool badStage(){
    return any(greaterThan(abs(stageFromVertex-vec4(rainProbe,skyProbe,frameProbe,effectProbe)),vec4(.0001)))
        ||abs(snowFromVertex-snowProbe)>.0001||abs(effectProbe-maxBlindnessDarkness)>.0001;
}
vec4 markerA(){return vec4(badStage()?vec3(1,0,1):vec3(rainProbe,skyProbe,snowProbe),1);}
vec4 markerB(){return vec4(badStage()?vec3(1,0,1):vec3(frameProbe/(1.+frameProbe),effectProbe,maxBlindnessDarkness),1);}
