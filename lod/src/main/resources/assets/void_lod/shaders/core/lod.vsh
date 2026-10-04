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
// with ambient occlusion (tops) or height above the floor (walls) in its alpha, and carry what they are made of in
// the second: a 16-bit material id, low byte in red (0: untextured). Smooth vertices carry the colour of the tops
// and of the risers, with the slope along x and z in the two alphas.
// Far blocky tiles (LOD_COMPACT) pack the same in two words (TileBuilder.COMPACT_BYTES): x, z, face and water, y in
// whole blocks; then the colour in 5 bits a channel, the corner occlusion level and the light in 5 bits each. They
// are untextured.
// Per tile (one instance each): its corner in blocks, its level, and its cross-fade (Fades: 0 solid, +n fading in,
// -n fading out, in 255ths).
#ifdef LOD_COMPACT
layout(location = 0) in uvec2 LodPacked;
layout(location = 1) in ivec4 LodTile;
#else
layout(location = 0) in ivec4 LodPos;
layout(location = 1) in vec4 LodTop;
layout(location = 2) in vec4 LodSide;
layout(location = 3) in ivec4 LodTile;
#endif

uniform sampler2D Sampler2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 horizontal;
layout(location = 4) flat out float tileFade;
layout(location = 7) out vec3 hazeColor;
#ifdef LOD_SMOOTH
layout(location = 2) out vec3 sideColor;
layout(location = 3) out vec4 stair;
layout(location = 5) out float surfaceY;
#ifdef LOD_CLIP
// 1 on land, 0 over the void: a triangle with no land in it at all is cut out
layout(location = 6) out float solid;
#endif
#else
// xyz: the position just inside the face's block; w: the face's material id, the same at every corner (it rides in
// the slot's spare component: blocky tiles are a great many small quads, and every varying slot costs)
layout(location = 2) out vec4 blockPos;
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

// Vanilla's open-air fog colour as it would be looking along dir: its base colour, pulled toward the sunrise colour
// as far as dir faces the sun's side of the sky, then toward the sky colour (AtmosphericFogEnvironment.getBaseColor).
vec3 airColor(vec3 dir) {
    float toward = max(dir.x * LodSkyBase.w, 0.0) * LodSunrise.a;
    return mix(mix(LodSkyBase.rgb, LodSunrise.rgb, toward), LodSkyColor.rgb, LodSkyColor.w);
}

void main() {
    // The vertex, whichever way it is packed: cell x and z, y in eighths, flags; the colours; the light (the sun's
    // horizon east and west, in radians, and the share of the sky the land hides).
    ivec4 vpos;
    vec4 top;
    vec4 side;
    float horizonEast, horizonWest, skyHidden;
#ifdef LOD_COMPACT
    uint w0 = LodPacked.x, w1 = LodPacked.y;
    vpos = ivec4(int(w0 & 127u), (int(w0) >> 18) * 8, int((w0 >> 7) & 127u), int((w0 >> 14) & 15u));
    // a top's alpha is its corner occlusion, one of vanilla's four shades; a wall's floor shading is under a pixel
    const float AO[4] = float[4](1.0, 0.8, 0.62, 0.48);
    top = vec4(vec3(float(w1 & 31u), float((w1 >> 5) & 31u), float((w1 >> 10) & 31u)) / 31.0,
        (vpos.w & 7) == 0 ? AO[(w1 >> 15) & 3u] : 1.0);
    side = vec4(0.0);
    // 5 bits of what was 8: the middle of each step, and none for none
    uint e = (w1 >> 17) & 31u, w = (w1 >> 22) & 31u, o = w1 >> 27;
    horizonEast = e == 0u ? 0.0 : float(e * 8u + 4u) * (1.5707963 / 255.0);
    horizonWest = w == 0u ? 0.0 : float(w * 8u + 4u) * (1.5707963 / 255.0);
    skyHidden = o == 0u ? 0.0 : float(o * 8u + 4u) / 255.0;
#else
    vpos = ivec4(LodPos.x & 255, LodPos.y, LodPos.z & 255, LodPos.w & 255);
    top = LodTop;
    side = LodSide;
    horizonEast = float((LodPos.x >> 8) & 255) * (1.5707963 / 255.0);
    horizonWest = float((LodPos.z >> 8) & 255) * (1.5707963 / 255.0);
    skyHidden = float((LodPos.w >> 8) & 255) / 255.0;
#endif

    // Integer arithmetic until the camera is subtracted, so the far edge of the world keeps its precision.
    int cell = 1 << LodTile.z;
    float worldY = float(vpos.y) * 0.125;
    ivec2 rel = ivec2(LodTile.x + vpos.x * cell, LodTile.y + vpos.z * cell) - CameraBlockPos.xz;
    vec3 pos = vec3(float(rel.x), worldY - float(CameraBlockPos.y), float(rel.y)) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vec3 toCamera = normalize(-pos);

    int face = vpos.w & 7;
    vec3 color;
    vec3 light = sample_lightmap(Sampler2, ivec2(0, 240)).rgb;
#ifdef LOD_SMOOTH
    // A stair of blocks seen from afar: tops in the top colour, risers in the side colour, each weighted by the
    // area it shows the camera. From above a hillside is its grass, from across the valley mostly its risers,
    // and a slope above the eye shows no tops at all. The fragment shader lays these out as columns of blocks.
    vec2 slope = vec2(decodeSlope(top.a), decodeSlope(side.a));
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
    color = top.rgb * relief;
    sideColor = side.rgb * light * relief;
    stair = vec4(wx / w, wz / w, slope);
    surfaceY = worldY;
#ifdef LOD_CLIP
    solid = (vpos.w & 16) != 0 ? 0.0 : 1.0;
#endif
#else
    // Minecraft's own directional shading: tops full, north and south a little darker, east and west more,
    // undersides (of End islands) darkest.
    color = top.rgb * (face == 0 ? 1.0 : (face <= 3 ? 0.8 : (face <= 5 ? 0.6 : 0.5)));
    vec3 normal = face == 0 ? vec3(0.0, 1.0, 0.0) : face == 2 ? vec3(0.0, 0.0, -1.0) : face == 3 ? vec3(0.0, 0.0, 1.0)
        : face == 4 ? vec3(-1.0, 0.0, 0.0) : face == 5 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, -1.0, 0.0);
    // just inside the block the face belongs to, so floor() names that block
    blockPos.xyz = vec3(float(rel.x), worldY - float(CameraBlockPos.y), float(rel.y)) - 0.5 * normal;
    blockPos.w = floor(side.r * 255.0 + 0.5) + floor(side.g * 255.0 + 0.5) * 256.0;
    occlusion = face == 0 ? vec2(top.a, 64.0) : vec2(1.0, top.a * 255.0);
#endif

    // Full sky light, through the lightmap: the LOD darkens at dusk and night exactly as the chunks do.
    color *= light;

    // The land's own light, which the chunks do not have, so it comes in past them: the shadows mountains cast
    // and the sky a valley does not see. The sun crosses from east to west over the zenith, so it is behind the
    // land wherever its elevation is under the horizon on its side; the edge is softened by a couple of degrees,
    // as wide as the sun and the haze make it.
    float beyond = smoothstep(LodClip.x, LodClip.x + 384.0, length(pos.xz));
    float sunElevation = asin(clamp(LodSun.y, -1.0, 1.0));
    float horizon = LodSun.x > 0.0 ? horizonEast : horizonWest;
    float sunlit = smoothstep(horizon - 0.03, horizon + 0.03, sunElevation);
    color *= mix(1.0, mix(0.55, 1.0, sunlit), LodSun.w * beyond) * (1.0 - 0.6 * skyHidden * beyond);

    // The haze takes the colour the sky has in this vertex's direction: at dusk the land toward the sun sinks into
    // the glow, the land away from it into the blue. Vanilla gives the whole frame the colour the camera faces; the
    // ratio to that keeps whatever it does to the colour afterwards (the night, the weather, the dimension).
    vec3 forward = -vec3(ModelViewMat[0][2], ModelViewMat[1][2], ModelViewMat[2][2]);
    hazeColor = FogColor.rgb * airColor(normalize(pos)) / max(airColor(forward), vec3(1e-3));
    // And vanilla paints the sunset's glow over the sky as a fan, centred on the horizon toward the sun and reaching
    // about 50 degrees either side (SkyRenderer.renderSunriseAndSunset): far land there sinks into the glow.
    vec2 across = normalize(pos.xz + vec2(1e-4, 0.0));
    float fan = LodSunrise.a * max(0.0, 1.0 - acos(clamp(across.x * LodSkyBase.w, -1.0, 1.0)) / 0.8727);
    hazeColor = mix(hazeColor, LodSunrise.rgb, fan);

    if ((vpos.w & 8) != 0) {
        // Water at a low angle mirrors the sky, right up to the seam: vanilla's own water there, seen as low,
        // shows the sky through it (its bed is out of the chunks' reach), so the two meet in the same colour.
        // Walls of water (the skirts under a tile's edge) too, as they show through vanilla's water. At night
        // the sky is dark, and so is the water.
        float fresnel = pow(1.0 - max(toCamera.y, 0.0), 5.0);
        color = mix(color, hazeColor, fresnel * 0.6);
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
