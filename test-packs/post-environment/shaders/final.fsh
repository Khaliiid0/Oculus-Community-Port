#version 120
uniform int moonPhase;
uniform float rainStrength;
uniform float rainFactor;
const int expectedMoon=7;
const float expectedRain=0.0;
void main(){
bool bad=moonPhase!=expectedMoon||abs(rainStrength-expectedRain)>.001||abs(rainFactor-rainStrength)>.001;
gl_FragColor=bad?vec4(1,0,1,1):vec4(0,1,0,1);
}
