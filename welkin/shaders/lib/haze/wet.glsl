#ifndef WELKIN_WET
#define WELKIN_WET

// VOID Welkin, module "haze" (M2): wet ground and the rain's light, for the `deferred` pass (near chunks and VOID
// LOD's far land alike: both arrive in the same gbuffer layout).
//
//   #include "/lib/haze/wet.glsl"
//   HzState st = hzState();                                  // or share the composite's, see NOTES
//   WelkinWet w = welkinWetSurface(st, albedo, rel, n, skyLight, hzWetClass(material));   // before lighting
//   ... light the surface (scale sunlight and ambient by welkinRainLight(st)) ...
//   lit = welkinWetLight(st, lit, w, rel, lightDir, lightColor, skyReflect);              // after lighting
//
// Wet ground: as void_wetness soaks in, open ground darkens (porous ground most, stone a little, metal not at all)
// and grows a little more saturated; walls take half, undersides none, and ground under a roof stays dry (sky light
// in the lightmap, eased off under the eaves). Puddles: on open flat ground near the camera, blobs whose outline sits
// on the block textures' own texel grid (16 a block near), growing with the wetness; their water mirrors the sky,
// more at a low angle, the sun (or moon) glints in a small soft spot, and rain drops flick single texels; a darker
// wet rim lies round them. In frost, puddles are ice. All the shading is smooth (the look of 2026-10-11): no steps,
// no dither; the puddles fade out smoothly toward their reach.

#include "/lib/haze/haze_config.glsl"
#include "/lib/haze/haze_uniforms.glsl"
#include "/lib/haze/haze_pixel.glsl"
#include "/lib/haze/haze_bridge.glsl"

// The pack's material class (plumbing's materials_table.glsl, M_*: pass the class, not the full block id) as a wet
// class. By name only, never by id range: the ids are generated and change. Plain compares, no lookup array (a const
// array indexed at run time lands in local memory on some drivers).
int hzWetClass(int m) {
#ifdef M_STONE
    if (m == M_SNOW || m == M_ICE || m == M_GLASS || m == M_GLASS_STAINED || m == M_WATER || m == M_LAVA
        || m == M_PORTAL || m == M_FIRE || m == M_TORCH || m == M_LAMP || m == M_INVISIBLE || m == M_GEL) return WET_DRY;
    if (m == M_PLANT || m == M_LEAVES_OTHER || m == M_LEAVES_SEASONAL || m == M_LEAVES_NEEDLE || m == M_HANGING
        || m == M_WALL_COVER || m == M_PLANT_TALL_LOWER || m == M_PLANT_TALL_UPPER || m == M_CROP
        || m == M_WATER_PLANT || m == M_FLOATING || m == M_GROUND_COVER || m == M_FUNGUS || m == M_CACTUS
        || m == M_STALK) return WET_PLANT;
    if (m == M_GRASS_BLOCK || m == M_SOIL || m == M_SAND || m == M_GRAVEL || m == M_MOSS || m == M_CLAY
        || m == M_WOOD || m == M_CLOTH || m == M_ORGANIC || m == M_FOOD) return WET_POROUS;
    if (m == M_METAL || m == M_GEM || m == M_MECHANISM) return WET_SHINY;
    return WET_SOLID;
#elif defined WELKIN_COMMON
    // M0's coarse ids (lib/common.glsl MAT_*, colortex2.y * 255)
    if (m == MAT_WATER || m == MAT_EMISSIVE || m == MAT_GLASS || m == MAT_SNOW || m == MAT_LAVA) return WET_DRY;
    if (m == MAT_FOLIAGE || m == MAT_LEAVES) return WET_PLANT;
    if (m == MAT_SAND) return WET_POROUS;
    return WET_SOLID;
#else
    return WET_SOLID;           // no material table yet: everything drawn is plain solid
#endif
}

struct WelkinWet {
    float wet;          // 0..1 how wet this surface is (wetness x exposure x facing x distance), continuous in time
    int   puddle;       // 0 none, 1 wet rim, 2 water, 3 ice
    float sheen;        // 0..1 grazing sheen strength for the class
    ivec2 grid;         // world texel id (hashes)
    vec3  relSnap;      // texel centre relative to the camera
    float texel;        // blocks per texel
    float fade;         // 1 near .. 0 at WET_PUDDLE_REACH: how much of the puddle shows
};

// Before lighting: darkens `albedo` in place and works out the puddle. rel: the surface relative to the camera;
// n: world normal (unit); skyLight: the lightmap's sky light 0..1; wetClass: hzWetClass(material).
WelkinWet welkinWetSurface(HzState s, inout vec3 albedo, vec3 rel, vec3 n, float skyLight, int wetClass) {
    WelkinWet w;
    w.wet = 0.0;
    w.puddle = 0;
    w.sheen = hzWetSheen(wetClass);
    float L = length(rel);
    HzTexel tx = hzTexel(rel, n, hzTexelBlocks(hzFootprint(L)));
    w.grid = tx.id;
    w.relSnap = tx.relSnap;
    w.texel = tx.size;
    w.fade = 0.0;
#ifdef WET_ENABLED
    if (s.wet <= 0.002 || wetClass == WET_DRY) return w;

    // open to the sky (eased off under the eaves), facing up, near enough (the wet look fades out over the far
    // land, where the camera's wetness says nothing), and not frozen
    float open = smoothstep(0.78, 0.94, skyLight);
    float facing = n.y > 0.5 ? 1.0 : (n.y > -0.5 ? 0.55 : 0.0);
    float near = 1.0 - smoothstep(1024.0, 2048.0, L);
    float soaked = s.wet * open * facing * near;
    if (soaked <= 0.0) return w;
    // in frost the ground is frozen, not wet: no darkening, and what puddles there are have frozen over
    w.wet = soaked * (1.0 - s.snow);

    // darker and a touch more saturated
    float l = hzLuma(albedo);
    albedo = mix(vec3(l), albedo, 1.0 + 0.15 * w.wet * float(wetClass == WET_POROUS));
    albedo *= 1.0 - hzWetDark(wetClass) * w.wet;

#ifdef WET_PUDDLES
    bool level = n.y > 0.9 && wetClass != WET_PLANT && skyLight > 0.9 && L < WET_PUDDLE_REACH;
    if (level && soaked > 0.3) {
        // a smooth fade toward the reach
        w.fade = 1.0 - smoothstep(0.7 * WET_PUDDLE_REACH, WET_PUDDLE_REACH, L);
        // the puddle field at the texel's centre: blobs of a few blocks, broken at the block scale
        vec2 local = cameraPositionFract.xz + tx.relSnap.xz;
        float field = 0.65 * hzWorldNoise(cameraPositionInt.xz, local, 2, 17) + 0.35 * hzWorldNoise(cameraPositionInt.xz, local, 1, 29);
        // more of the ground under water as it soaks: none below 0.3 wetness, about a quarter when soaked
        float edge = mix(0.86, 0.58, smoothstep(0.3, 1.0, s.wet));
        if (field > edge) {
            w.puddle = (s.temp < -0.2 || s.snow > 0.5) ? 3 : 2;
        } else if (field > edge - 0.07 && w.wet > 0.0) {
            // the soaked ground round a puddle: darker the nearer the water
            w.puddle = 1;
            albedo *= 1.0 - 0.18 * w.fade * smoothstep(edge - 0.07, edge, field);
        }
        if (w.puddle == 2) {
            albedo *= 1.0 - 0.5 * w.fade;       // the ground seen through the water is darker; the sky shows in it later
        } else if (w.puddle == 3) {
            albedo = mix(albedo, HZ_PUDDLE_ICE * max(hzLuma(albedo) * 1.6, 0.35), 0.6 * w.fade);
        }
    }
#endif
#endif
    return w;
}

// After lighting: the sky in the puddles, glints and drops, and a wet sheen. n: the surface's world normal; lightDir:
// toward the sun or moon (world, unit; Iris shadowLightPosition turned to world); lightColor: its light as the pack
// lights with it (0 when hidden); skyReflect: what the surface mirrors along reflect(view, normal), the sky with its
// gradient (and in puddles the clouds overhead), worked out by the caller.
vec3 welkinWetLight(HzState s, vec3 lit, WelkinWet w, vec3 n, vec3 lightDir, vec3 lightColor, vec3 skyReflect) {
#ifdef WET_ENABLED
    if (w.wet <= 0.0 && w.puddle == 0) return lit;
    vec3 v = normalize(w.relSnap);
    if (w.puddle >= 2) {
        float c = clamp(-v.y, 0.0, 1.0);                        // cosine to the up normal
        // a water film: the darkened ground under it, the sky over it by a Fresnel term (a fifth looking straight
        // down, nearly a mirror at a low angle)
        float fres = 0.22 + 0.72 * pow(1.0 - c, 3.0);
        vec3 mirror = w.puddle == 3 ? mix(skyReflect, HZ_PUDDLE_ICE * hzLuma(skyReflect) * 1.2, 0.4) : skyReflect;
        lit = mix(lit, mirror, fres * (w.puddle == 3 ? 0.5 : 1.0) * w.fade);
        // the light's glint: a small soft spot where the reflected view meets the sun or moon
        vec3 r = reflect(v, vec3(0.0, 1.0, 0.0));
        float g = dot(r, lightDir);
        float glint = smoothstep(0.9984, 0.9998, g);
        lit += lightColor * glint * 0.9 * (1.0 - 0.8 * s.rain) * step(0.0, lightDir.y) * w.fade;
        // drops: a texel (an eighth of a block near) flicks to the bright step and back, as often as it rains
        if (w.puddle == 2 && s.rain > 0.05) {
            ivec2 drop = w.grid >> 1;
            float phase = hzHash(drop, 5);
            float tick = floor(frameTimeCounter * 8.0 + phase * 8.0);
            if (hzHash(drop + ivec2(int(tick) * 7, 0), 11) < 0.035 * s.rain) {
                lit = mix(lit, skyReflect * 1.25 + 0.04, 0.6 * w.fade);
            }
        }
    } else if (w.sheen > 0.0 && w.wet > 0.0) {
        // wet ground and leaves: a thin gloss of the sky, stronger at a low angle, and a soft highlight of the sun
        float gloss = w.wet * w.sheen;
        float c = clamp(dot(-v, n), 0.0, 1.0);
        float fres = 0.05 + 0.7 * pow(1.0 - c, 4.0);
        lit = mix(lit, skyReflect, clamp(gloss * fres * 1.6, 0.0, 0.5));
        vec3 hv = normalize(lightDir - v);
        float spec = pow(clamp(dot(n, hv), 0.0, 1.0), 48.0) * gloss * 1.1 * step(0.0, dot(n, lightDir));
        lit += lightColor * spec;
    }
#endif
    return lit;
}

// Debug view 4 (HAZE_DEBUG == 4): what the wet pass decided, drawn by deferred in place of the lit colour.
vec3 welkinWetDebug(WelkinWet w) {
    if (w.puddle == 1) return vec3(0.1, 0.2, 0.5);
    if (w.puddle == 2) return vec3(0.5, 0.75, 1.0);
    if (w.puddle == 3) return vec3(1.0);
    return vec3(w.wet * 0.8);
}

// The rain's light at the camera, for deferred: rgb a cool tint for the ambient (sky) light that keeps its brightness
// (M0's welkinLight already darkens sky and sun light by rainStrength, which Gloam drives regionally: do not darken
// twice); a, for a light ramp without that, how much direct sun or moon light the rain's cloud leaves. Flat over the
// frame; 1 with no rain. Cloud shadows (M3) are separate and multiply on top.
vec4 welkinRainLight(HzState s) {
    float r = s.rain * s.outdoor;
    return vec4(mix(vec3(1.0), vec3(0.952, 0.998, 1.088), r), 1.0 - 0.6 * r);
}

#endif
