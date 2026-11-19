#version 120
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
uniform sampler2D texture;
uniform sampler2D lightmap;
void main(){gl_FragColor=texture2D(texture,uv)*tint*vec4(texture2D(lightmap,lm).rgb,1.0);}
