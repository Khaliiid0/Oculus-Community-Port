uniform float far;
varying float observedFar;
void main(){observedFar=far;gl_Position=ftransform();}
