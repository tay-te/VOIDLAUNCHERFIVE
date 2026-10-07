#ifndef GLOAM_FARINFO_GLSL
#define GLOAM_FARINFO_GLSL

// What every part of the far air is drawn with this frame (FarAir.prepare). Positions are relative to the camera, as
// the chunks' are; world-anchored things (the noise, the weather map) get the camera's place in their own frame.
layout(std140) uniform GloamFar {
    // the camera's rotation: camera-relative world to view, as ModelViewMat is for the chunks
    mat4 FarModelView;
    // x: the camera's world y, y: game seconds (wrapping each hour), z: where vanilla's chunks end (blocks across the
    // ground), w: the far air's reach (VOID LOD's radius, or vanilla's cloud range without it)
    vec4 FarCamera;
    // xyz: the camera from the mist mesh's anchor, w: the haze's reach (blocks for two thirds of a cloud to go)
    vec4 FarAnchor;
    // xyz: toward the sun, w: how strongly it lights (0 once it has set)
    vec4 FarSun;
    // vanilla's CLOUD_COLOR (time of day, weather and Gloam's overcast already in it)
    vec4 FarCloudColor;
    // vanilla's SUNRISE_SUNSET_COLOR: rgb and its strength
    vec4 FarSunrise;
    // x: the cloud deck's base (CLOUD_HEIGHT), y: the highest a storm tops it by in view, z: 1 fancy, 0 fast,
    // w: the sun's side (+1 east, -1 west)
    vec4 FarLayer;
    // x: vanilla's rain level, y: 1 if the fronts bring showers under a clear sky, z: how far into summer (storms come
    // lower), w: vanilla's thunder level
    vec4 FarWeather;
    // xy: the camera in the weather map (blocks from its corner, in the fronts' drifting frame), z: 1 / its size in
    // blocks, w: unused
    vec4 FarMap;
    // xy: the camera in the fronts' frame, zw: the camera in the world; both wrapped to 65536 blocks, which every
    // noise scale divides
    vec4 FarNoise;
    // the cloud shadows' picture (far_shade, laid on VOID LOD's land): xy its corner from the camera, z its size in
    // blocks, w the ground height the shadows are worked out for
    vec4 FarShade;
};

// The weather a front of f brings, as Air works it out (Air.rain, Air.cloudCover, Air.storm): x cover, y storm, z rain.
vec3 farWeather(float f) {
    float rain = max(FarWeather.x * smoothstep(0.18, 0.42, f), FarWeather.y * 0.6 * smoothstep(0.74, 0.9, f));
    float cover = max(smoothstep(0.30, 0.75, f), rain * 0.95);
    float storm = max(smoothstep(0.9 - 0.05 * FarWeather.z, 0.98, f), FarWeather.w * smoothstep(0.3, 0.6, f));
    return vec3(cover, storm, rain);
}

// Interleaved gradient noise: a dither with no visible grid, to hide the steps of a ray march.
float farDither(vec2 p) {
    return fract(52.9829189 * fract(dot(floor(p), vec2(0.06711056, 0.00583715))));
}

#endif
