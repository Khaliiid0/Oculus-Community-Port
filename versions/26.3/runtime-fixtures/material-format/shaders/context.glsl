#ifdef MC_TEXTURE_FORMAT_LAB_PBR_1_3
#ifndef MC_TEXTURE_FORMAT_LAB_PBR
#error Version macro must imply generic LabPBR
#endif
#define RESOURCE_FORMAT_CODE 2
#elif defined(MC_TEXTURE_FORMAT_LAB_PBR)
#define RESOURCE_FORMAT_CODE 1
#else
#define RESOURCE_FORMAT_CODE 0
#endif
