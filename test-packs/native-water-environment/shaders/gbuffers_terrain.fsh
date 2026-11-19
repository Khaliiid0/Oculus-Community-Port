uniform int isEyeInWater;
uniform float darknessFactor;
uniform vec3 fogColor;
void main(){gl_FragColor=vec4(float(isEyeInWater)/3.0,darknessFactor,fogColor.r,1.0);}
