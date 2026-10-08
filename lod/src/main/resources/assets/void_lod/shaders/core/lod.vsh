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
// in 255ths of a right angle, and how much of the sky the land around hides (top 5 bits of the flags' high byte),
// with the block light over the surface in the 3 bits under it. Blocky faces use the first colour,
// with ambient occlusion (tops) or height above the floor (walls) in its alpha, and carry what they are made of in
// the second: a 16-bit material id, low byte in red (0: untextured). Smooth vertices carry the colour of the tops
// and of the risers, with the slope along x and z in the two alphas.
// Far blocky tiles (LOD_COMPACT) have no vertices: one record per quad (TileBuilder.RECORD_BYTES), read for each
// corner by the vertex's index. Its lowest corner (x, z, face and water, y in whole blocks); its size along the face,
// the rotation of its corners and each top corner's occlusion level, the block light; the colour along its top and
// along its foot in 5 bits a channel; the horizons in 4 bits each and the sky's occlusion in 5. They are untextured.
// Per tile (one instance each): its corner in blocks, its level, and its cross-fade (Fades: 0 solid, +n fading in,
// -n fading out, in 255ths).
#ifdef LOD_COMPACT
layout(location = 1) in ivec4 LodTile;
uniform usamplerBuffer LodQuads;
// each face's corners, unrotated, as TileBuilder.CORNERS: u | v << 1 along the face's two axes
const int CORNERS[32] = int[32](0, 2, 3, 1, 0, 0, 0, 0, 2, 3, 1, 0, 3, 2, 0, 1, 3, 2, 0, 1, 2, 3, 1, 0, 0, 1, 3, 2, 0, 0, 0, 0);
#else
layout(location = 0) in ivec4 LodPos;
layout(location = 1) in vec4 LodTop;
layout(location = 2) in vec4 LodSide;
layout(location = 3) in ivec4 LodTile;
#endif

uniform sampler2D Sampler2;

// The seasons (LodApi.setSeasonTints): per class and variant (class * 4 + variant), the colour a classed face moves
// toward and how far (w), then how much brighter or darker it is (x).
layout(std140) uniform LodSeasons {
    vec4 SeasonTarget[128];
    vec4 SeasonBright[128];
};

// A grass or leaf face's colour this time of year (LodApi.seasonColour does the same): toward the target, keeping its
// own light and shade, then brightened or darkened. Code: the class in the low five bits (0: never changes), the variant
// above.
vec3 season(vec3 rgb, int code) {
    int cls = code & 31;
    if (cls == 0) {
        return rgb;
    }
    int e = cls * 4 + ((code >> 5) & 3);
    vec4 t = SeasonTarget[e];
    const vec3 L = vec3(0.2126, 0.7152, 0.0722);
    vec3 target = t.rgb * (dot(rgb, L) / max(dot(t.rgb, L), 1e-3));
    return clamp(mix(rgb, target, t.w) * SeasonBright[e].x, 0.0, 1.0);
}

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 horizontal;
layout(location = 4) flat out float tileFade;
layout(location = 7) out vec3 hazeColor;
// 1 where vanilla does not draw its chunks for being too far above or below the camera (more than its render distance in
// sections): there the LOD draws inside the chunks' radius too
layout(location = 8) out float beyondChunks;
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
    float horizonEast, horizonWest, skyHidden, blockLight;
#ifdef LOD_COMPACT
    uvec4 quad = texelFetch(LodQuads, gl_VertexIndex >> 2);
    uint w0 = quad.x, w1 = quad.y;
    int f = int((w0 >> 14) & 7u);
    int c = ((gl_VertexIndex & 3) + int((w1 >> 20) & 3u)) & 3;
    int uv = CORNERS[f * 4 + c];
    int du = (uv & 1) * int(w1 & 127u), dv = (uv >> 1) * int((w1 >> 7) & 8191u);
    ivec3 at = ivec3(int(w0 & 127u), int(w0) >> 18, int((w0 >> 7) & 127u));
    at += f == 0 || f == 6 ? ivec3(du, 0, dv) : f >= 4 ? ivec3(0, dv, du) : ivec3(du, dv, 0);
    vpos = ivec4(at.x, at.y * 8, at.z, int((w0 >> 14) & 15u));
    // a top's alpha is its corner occlusion, one of vanilla's four shades; a wall's floor shading is under a pixel;
    // a wall's colour runs from its top's down to its foot's
    const float AO[4] = float[4](1.0, 0.8, 0.62, 0.48);
    uint rgb = (uv >> 1) != 0 || f == 0 || f == 6 ? quad.z & 32767u : (quad.z >> 15) & 32767u;
    top = vec4(vec3(float(rgb & 31u), float((rgb >> 5) & 31u), float((rgb >> 10) & 31u)) / 31.0,
        f == 0 ? AO[(w1 >> (22 + 2 * c)) & 3u] : 1.0);
    side = vec4(0.0);
    // 4 or 5 bits of what was 8: the middle of each step, and none for none
    uint e = quad.w & 15u, w = (quad.w >> 4) & 15u, b = w1 >> 30, o = (quad.w >> 8) & 31u;
    top.rgb = season(top.rgb, int((quad.w >> 13) & 127u));
    horizonEast = e == 0u ? 0.0 : float(e * 16u + 8u) * (1.5707963 / 255.0);
    horizonWest = w == 0u ? 0.0 : float(w * 16u + 8u) * (1.5707963 / 255.0);
    skyHidden = o == 0u ? 0.0 : float(o * 8u + 4u) / 255.0;
    blockLight = float(b) * 5.0;
#else
    vpos = ivec4(LodPos.x & 255, LodPos.y, LodPos.z & 255, LodPos.w & 255);
    top = LodTop;
    side = LodSide;
    if ((vpos.w & 7) != 1) {
        // a blocky face carries its season code in the second colour's blue
        top.rgb = season(top.rgb, int(side.b * 255.0 + 0.5));
    }
    horizonEast = float((LodPos.x >> 8) & 255) * (1.5707963 / 255.0);
    horizonWest = float((LodPos.z >> 8) & 255) * (1.5707963 / 255.0);
    int o = (LodPos.w >> 11) & 31;
    skyHidden = o == 0 ? 0.0 : float(o * 8 + 4) / 255.0;
    blockLight = float((LodPos.w >> 8) & 7) * (15.0 / 7.0);
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
    // the block light over the surface (torches, lanterns, lava) through the same lightmap: a lit town glows at night
    vec3 light = sample_lightmap(Sampler2, ivec2(int(blockLight * 16.0 + 0.5), 240)).rgb;
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

    // Full sky light and the surface's block light, through the lightmap: the LOD darkens at dusk and night exactly as
    // the chunks do, and what was lit stays lit.
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
    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;
    beyondChunks = LodClip.z > 0.0 && abs(floor(worldY / 16.0) - floor(cameraY / 16.0)) > LodClip.z ? 1.0 : 0.0;
}
