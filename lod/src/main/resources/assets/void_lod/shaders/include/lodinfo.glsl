#ifndef VOID_LOD_LODINFO_GLSL
#define VOID_LOD_LODINFO_GLSL

layout(std140) uniform LodInfo {
    mat4 ModelViewMat;
    // x: radius around the camera that vanilla's chunks draw; y: width of the dithered seam beyond it
    vec4 LodClip;
    // Aerial haze. x: 1 / the haze's reach at the base height (0: use vanilla's fog instead), y: 1 / its scale
    // height, z: the base height (sea level), w: how hard it rains, 0..1
    vec4 LodHaze;
    // The sun: xyz its direction, w how strongly it models the far relief (0 at night)
    vec4 LodSun;
    // What vanilla's fog colour is made of in open air (AtmosphericFogEnvironment), to work it out for every
    // direction rather than only the one the camera faces: the base fog colour and the sun's side (+1 east, -1 west),
    // the sunrise colour and its strength, the sky colour and how much of it the fog takes.
    vec4 LodSkyBase;
    vec4 LodSunrise;
    vec4 LodSkyColor;
};

#endif
