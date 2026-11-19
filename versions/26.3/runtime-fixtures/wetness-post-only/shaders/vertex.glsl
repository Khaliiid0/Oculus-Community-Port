uniform float wetness;
varying float vertexWetness;
void main(){vertexWetness=wetness;gl_Position=ftransform();}
