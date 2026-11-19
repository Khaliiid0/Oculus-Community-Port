#version 120
/* DRAWBUFFERS:7 */
const bool colortex7Clear=false;
const vec4 colortex7ClearColor=vec4(0.0);
const bool forceFailure=false;
uniform sampler2D colortex7;
uniform vec3 previousCameraPositionFract;
uniform vec3 cameraPositionFract;
uniform float velocity;
uniform mat4 gbufferProjection;
uniform mat4 gbufferPreviousProjection;
uniform mat4 gbufferModelView;
uniform mat4 gbufferPreviousModelView;
varying vec2 texcoord;
bool different(mat4 a,mat4 b){
return any(greaterThan(abs(a[0]-b[0]),vec4(.0001)))
||any(greaterThan(abs(a[1]-b[1]),vec4(.0001)))
||any(greaterThan(abs(a[2]-b[2]),vec4(.0001)))
||any(greaterThan(abs(a[3]-b[3]),vec4(.0001)));}
void main(){
vec4 history=texture2D(colortex7,texcoord);
if(history.a>.5){gl_FragColor=history;return;}
bool bad=forceFailure||any(greaterThan(abs(previousCameraPositionFract-cameraPositionFract),vec3(.0001)))
||abs(velocity)>.0001||different(gbufferProjection,gbufferPreviousProjection)
||different(gbufferModelView,gbufferPreviousModelView);
gl_FragColor=vec4(bad?1.0:0.0,0,0,1);
}
