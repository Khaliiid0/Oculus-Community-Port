#version 120
/* RENDERTARGETS:0,1,2,3,4,5,6,7 */
uniform int frameCounter;
const int colortex0Format = R8;
const int colortex1Format = R8;
const int colortex2Format = R8;
const int colortex3Format = R8;
const int colortex4Format = R8;
const int colortex5Format = R8;
const int colortex6Format = R8;
const int colortex7Format = R8;
void main(){
    float parity=float(frameCounter%2)*0.5;
    gl_FragData[0]=vec4(1.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[1]=vec4(2.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[2]=vec4(3.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[3]=vec4(4.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[4]=vec4(5.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[5]=vec4(6.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[6]=vec4(7.0/32.0+parity,0.0,0.0,1.0);
    gl_FragData[7]=vec4(8.0/32.0+parity,0.0,0.0,1.0);
}
