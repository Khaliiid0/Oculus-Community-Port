#version 120
uniform float wetness;
uniform float rainStrength;
varying vec2 texcoord;
void main(){
if(texcoord.x<.5){gl_FragColor=vec4(rainStrength,rainStrength,rainStrength,1);}
else if(wetness<.1){gl_FragColor=vec4(0,1,0,1);}
else if(wetness>.25){gl_FragColor=vec4(1,0,1,1);}
else {gl_FragColor=vec4(1,1,0,1);}
}
