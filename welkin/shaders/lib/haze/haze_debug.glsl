#ifndef WELKIN_HAZE_DEBUG
#define WELKIN_HAZE_DEBUG

// Debug views for the haze module (option HAZE_DEBUG), drawn by composite in place of the scene:
//   1 kind map: the haze kind of the land under each surface, one flat colour per kind (sky: the camera's kind)
//   2 opacity steps: back haze in red, front haze in green, the world's edge folded into red
//   3 tile source: near tile green, far tile blue, fallback red (shows the N/F hand-over and the tile moving)
//   4 wet: wetness grey, rim dark blue, puddle water light blue, ice white (from deferred, see NOTES)

#include "/lib/haze/haze.glsl"

const vec3 HZ_DEBUG_KIND[HZ_KINDS] = vec3[HZ_KINDS](
    vec3(0.5), vec3(0.55, 0.75, 0.45), vec3(0.45, 0.65, 0.95), vec3(0.95, 0.95, 1.0),
    vec3(0.15, 0.45, 0.25), vec3(0.90, 0.65, 0.25), vec3(0.40, 0.85, 1.0)
);

// Returns true and the debug colour in `c` when a debug view is on.
bool welkinHazeDebug(HzState s, WelkinHaze h, vec3 rel, bool isSky, inout vec3 c) {
#if HAZE_DEBUG == 1 || HAZE_DEBUG == 3
    HzLand land = isSky ? s.here : hzLand(s, cameraPositionFract.xz + rel.xz, h.dp);
#if HAZE_DEBUG == 1
    c = HZ_DEBUG_KIND[land.kind] * (s.mode == HZ_NONE ? 0.5 : 1.0);
#else
    c = land.source == 1 ? vec3(0.2, 0.9, 0.3) : (land.source == 2 ? vec3(0.2, 0.4, 1.0) : vec3(1.0, 0.2, 0.2));
    // the tile's cell grid, so it can be seen sliding with the camera
    ivec2 cell = (cameraPositionInt.xz >> 4) + ivec2(floor((vec2(cameraPositionInt.xz & 15) + cameraPositionFract.xz + rel.xz) / 16.0));
    if (land.source == 1 && ((cell.x ^ cell.y) & 1) == 0) c *= 0.8;
#endif
    return true;
#elif HAZE_DEBUG == 2
    c = vec3(h.back.a, h.front.a, isSky ? 0.3 : 0.0);
    return true;
#else
    return false;
#endif
}

#endif
