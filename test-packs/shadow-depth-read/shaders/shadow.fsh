#version 120
/* DRAWBUFFERS:0 */
const int shadowMapResolution=512;
const float shadowDistance=128.0;
uniform int frameCounter;
void main(){
int marker=frameCounter%65536;
gl_FragColor=vec4(gl_FragCoord.z,float(marker%256)/255.0,float(marker/256)/255.0,1);
}
