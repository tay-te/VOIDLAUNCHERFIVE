#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <void_lod:lodinfo.glsl>

// Per vertex: x and z in cells from the tile's corner (low bytes), y in eighths of a block, flags (face in the low
// three bits: 0 up, 1 smooth, 2/3 north/south, 4/5 west/east, 6 down; +8 for water, +16 over the void), then two
// colours. The high bytes of x, z and flags are the light (Horizons): the sun's horizon to the east and to the west,
// in 255ths of a right angle, and how much of the sky the land around hides. Blocky faces use the first colour,
// with ambient occlusion (tops) or height above the floor (walls) in its alpha. Smooth vertices carry the colour of
// the tops and of the risers, with the slope along x and z in the two alphas.
// Per tile (one instance each): its corner in blocks, its level, and its cross-fade (Fades: 0 solid, +n fading in,
// -n fading out, in 255ths).
layout(location = 0) in ivec4 LodPos;
layout(location = 1) in vec4 LodTop;
layout(location = 2) in vec4 LodSide;
layout(location = 3) in ivec4 LodTile;

uniform sampler2D Sampler2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 horizontal;
layout(location = 4) flat out float tileFade;
#ifdef LOD_SMOOTH
layout(location = 2) out vec3 sideColor;
layout(location = 3) out vec4 stair;
layout(location = 5) out float surfaceY;
#ifdef LOD_CLIP
// 1 on land, 0 over the void: a triangle with no land in it at all is cut out
layout(location = 6) out float solid;
#endif
#else
layout(location = 2) out vec3 blockPos;
layout(location = 3) out vec2 occlusion;
#endif

float decodeSlope(float a) {
    float e = floor(a * 255.0 + 0.5) - 128.0;
    return e * abs(e) / 2048.0;
}

// The optical depth of the air between the camera and a point, for haze that thins with height: dense in the
// valleys, thin over the peaks, so a far range stands clear above the haze that fills the land in front of it.
// Rain is a curtain on top that does not thin with height (VoidLod.RAIN_REACH, starting VoidLod.RAIN_OFFSET
// blocks behind the camera as vanilla's rain fog does), so a downpour hides the far land from a peak too.
float haze(vec3 pos, float worldY) {
    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;
    float k = (worldY - cameraY) * LodHaze.y;
    float along = abs(k) < 1e-3 ? 1.0 - 0.5 * k : (1.0 - exp(-k)) / k;
    float density = exp(clamp((LodHaze.z - cameraY) * LodHaze.y, -8.0, 4.0));
    float rain = (length(pos) + 160.0 * LodHaze.w) * LodHaze.w / 660.0;
    return 1.0 - exp(-length(pos) * LodHaze.x * density * along - rain);
}

void main() {
    // Integer arithmetic until the camera is subtracted, so the far edge of the world keeps its precision.
    int cell = 1 << LodTile.z;
    float worldY = float(LodPos.y) * 0.125;
    ivec2 rel = ivec2(LodTile.x + (LodPos.x & 255) * cell, LodTile.y + (LodPos.z & 255) * cell) - CameraBlockPos.xz;
    vec3 pos = vec3(float(rel.x), worldY - float(CameraBlockPos.y), float(rel.y)) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vec3 toCamera = normalize(-pos);

    int face = LodPos.w & 7;
    vec3 color;
    vec3 light = sample_lightmap(Sampler2, ivec2(0, 240)).rgb;
#ifdef LOD_SMOOTH
    // A stair of blocks seen from afar: tops in the top colour, risers in the side colour, each weighted by the
    // area it shows the camera. From above a hillside is its grass, from across the valley mostly its risers,
    // and a slope above the eye shows no tops at all. The fragment shader lays these out as columns of blocks.
    vec2 slope = vec2(decodeSlope(LodTop.a), decodeSlope(LodSide.a));
    vec3 n = normalize(vec3(-slope.x, 1.0, -slope.y));
    float wt = n.y * max(toCamera.y, 0.0) + 0.002;
    float wx = abs(n.x) * max(sign(n.x) * toCamera.x, 0.0);
    float wz = abs(n.z) * max(sign(n.z) * toCamera.z, 0.0);
    float w = wt + wx + wz;
    // The sun models the far relief: slopes toward it a little brighter, slopes away a little darker, a fifth
    // either way at most (Minecraft's face shading already darkens the walls). The chunks have no such light, so
    // it fades in past the first kilometre or so, where relief is all a range shows.
    float toward = clamp(dot(n, LodSun.xyz) - LodSun.y, -0.6, 0.6);
    float relief = 1.0 + 0.35 * LodSun.w * smoothstep(400.0, 1800.0, length(pos.xz)) * toward;
    color = LodTop.rgb * relief;
    sideColor = LodSide.rgb * light * relief;
    stair = vec4(wx / w, wz / w, slope);
    surfaceY = worldY;
#ifdef LOD_CLIP
    solid = (LodPos.w & 16) != 0 ? 0.0 : 1.0;
#endif
#else
    // Minecraft's own directional shading: tops full, north and south a little darker, east and west more,
    // undersides (of End islands) darkest.
    color = LodTop.rgb * (face == 0 ? 1.0 : (face <= 3 ? 0.8 : (face <= 5 ? 0.6 : 0.5)));
    vec3 normal = face == 0 ? vec3(0.0, 1.0, 0.0) : face == 2 ? vec3(0.0, 0.0, -1.0) : face == 3 ? vec3(0.0, 0.0, 1.0)
        : face == 4 ? vec3(-1.0, 0.0, 0.0) : face == 5 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, -1.0, 0.0);
    // just inside the block the face belongs to, so floor() names that block
    blockPos = vec3(float(rel.x), worldY - float(CameraBlockPos.y), float(rel.y)) - 0.5 * normal;
    occlusion = face == 0 ? vec2(LodTop.a, 64.0) : vec2(1.0, LodTop.a * 255.0);
#endif

    // Full sky light, through the lightmap: the LOD darkens at dusk and night exactly as the chunks do.
    color *= light;

    // The land's own light, which the chunks do not have, so it comes in past them: the shadows mountains cast
    // and the sky a valley does not see. The sun crosses from east to west over the zenith, so it is behind the
    // land wherever its elevation is under the horizon on its side; the edge is softened by a couple of degrees,
    // as wide as the sun and the haze make it.
    float beyond = smoothstep(LodClip.x, LodClip.x + 384.0, length(pos.xz));
    float sunElevation = asin(clamp(LodSun.y, -1.0, 1.0));
    float horizon = float(((LodSun.x > 0.0 ? LodPos.x : LodPos.z) >> 8) & 255) * (1.5707963 / 255.0);
    float sunlit = smoothstep(horizon - 0.03, horizon + 0.03, sunElevation);
    float skyHidden = float((LodPos.w >> 8) & 255) / 255.0;
    color *= mix(1.0, mix(0.55, 1.0, sunlit), LodSun.w * beyond) * (1.0 - 0.6 * skyHidden * beyond);

    if ((LodPos.w & 8) != 0) {
        // Water at a low angle mirrors the sky, right up to the seam: vanilla's own water there, seen as low,
        // shows the sky through it (its bed is out of the chunks' reach), so the two meet in the same colour.
        // Walls of water (the skirts under a tile's edge) too, as they show through vanilla's water. At night
        // the sky is dark, and so is the water.
        float fresnel = pow(1.0 - max(toCamera.y, 0.0), 5.0);
        color = mix(color, FogColor.rgb, fresnel * 0.6);
    }

    float cylindrical = fog_cylindrical_distance(pos);
    float fog;
    if (LodHaze.x > 0.0) {
        fog = max(haze(pos, worldY), linear_fog_value(cylindrical, FogRenderDistanceStart, FogRenderDistanceEnd));
    } else {
        fog = total_fog_value(fog_spherical_distance(pos), cylindrical, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    }
    vertexColor = vec4(color, fog);
    horizontal = pos.xz;
    tileFade = float(LodTile.w) / 255.0;
}
