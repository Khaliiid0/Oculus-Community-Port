#version 120
/* RENDERTARGETS:8,9,10,11,12,13,14,15 */
uniform int frameCounter;
const int colortex8Format = R8;
const int colortex9Format = R8;
const int colortex10Format = R8;
const int colortex11Format = R8;
const int colortex12Format = R8;
const int colortex13Format = R8;
const int colortex14Format = R8;
const int colortex15Format = R8;
void main(){
    float parity=float(frameCounter%2)*0.5;
    gl_FragData[0]=vec4(9.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[1]=vec4(10.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[2]=vec4(11.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[3]=vec4(12.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[4]=vec4(13.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[5]=vec4(14.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[6]=vec4(15.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[7]=vec4(16.0/32.0+parity,0.0,0.0,1.0);
}
