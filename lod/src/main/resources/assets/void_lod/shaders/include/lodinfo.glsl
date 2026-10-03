#ifndef VOID_LOD_LODINFO_GLSL
#define VOID_LOD_LODINFO_GLSL

layout(std140) uniform LodInfo {
    mat4 ModelViewMat;
    // x: radius around the camera that vanilla's chunks draw; y: width of the dithered seam beyond it
    vec4 LodClip;
};

#endif
