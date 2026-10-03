#ifndef VOID_LOD_LODINFO_GLSL
#define VOID_LOD_LODINFO_GLSL

layout(std140) uniform LodInfo {
    mat4 ModelViewMat;
    // x: radius around the camera that vanilla's chunks draw; y: width of the dithered seam beyond it
    vec4 LodClip;
    // Aerial haze. x: 1 / the haze's reach at the base height (0: use vanilla's fog instead), y: 1 / its scale
    // height, z: the base height (sea level), w: blocks of haze before the first (rain's)
    vec4 LodHaze;
};

#endif
