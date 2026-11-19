uniform sampler2D noisetex;
uniform float missingLibraryInput;
vec4 deadNoise(){return texture2D(noisetex,vec2(.5))*missingLibraryInput;}
vec4 deadWrapper(){return deadNoise();}
vec3 markerLeaf(){return vec3(.25,.5,.75);}
vec3 activeMarker(){return markerLeaf();}
