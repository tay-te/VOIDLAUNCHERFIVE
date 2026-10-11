// VOID Welkin materials: decode the IDs from block.properties, item.properties and entity.properties.
//
// ID scheme (written by tools/gen_ids.py, which also writes lib/materials_table.glsl):
//   block / item id = 10000 + M + 100 * L     M = material class 0..99, L = light colour class 0..15
//   entity id       = 20000 + E
//   unmapped        = -1 (Iris default) -> M_GENERIC (0), L_NONE, E_GENERIC
// M 0..9 are the skeleton's MAT_* numbers (lib/common.glsl), so `material == MAT_WATER` keeps meaning water;
// finer classes start at 10 and the mat*() family tests below cover both.
// How bright an emitter is comes from at_midBlock.w (the block's real light level, 0..15), never from the ID,
// so an unlit furnace carries L_FLAME but does not glow.
//
// G-buffer: colortex2.y = M / 255 (as the skeleton already writes it), colortex2.w = L / 15 (was unused).
// Cost: every function is a few integer ops and at most one constant-array read.
// Include after lib/common.glsl (or lib/settings.glsl).
#ifndef WELKIN_MATERIALS
#define WELKIN_MATERIALS

#include "/lib/materials_table.glsl"

// ---- bit layout of WELKIN_MAT_BITS[m]
#define MATB_SWAY_MASK    0x0000F  // bits 0..3: sway kind (SWAY_*)
#define MATB_ROUGH_SHIFT  4        // bits 4..5: 0 matte, 1 satin, 2 glossy, 3 polished
#define MATB_POROUS_SHIFT 6        // bits 6..7: 0 none .. 3 soaks up a lot of rain
#define MATF_SSS          0x00100  // light shows through from behind (leaves, grass)
#define MATF_SNOW         0x00200  // up faces take settled snow and frost
#define MATF_FULLGLOW     0x00400  // the whole texture glows when the block emits light
#define MATF_FOLIAGE      0x00800  // living plant: seasonal touches apply
#define MATF_CROWN        0x01000  // Gloam swaps this crown's model through the year
#define MATF_METAL        0x02000  // specular takes the albedo's colour
#define MATF_TRANSLUCENT  0x04000  // drawn in gbuffers_water, not the opaque pass
#define MATF_PUDDLE       0x08000  // flat up faces may hold puddles
#define MATF_SPARKLE      0x10000  // frost and crystal glints in steps
#define MATF_DAMP         0x20000  // darkens in dripping caves

#define SWAY_NONE        0
#define SWAY_LEAVES      1
#define SWAY_PLANT       2   // anchored at the bottom: only the top vertices move
#define SWAY_TALL_LOWER  3   // lower half of a two-block plant: top vertices move half as far
#define SWAY_TALL_UPPER  4   // upper half: bottom vertices move half, top vertices fully
#define SWAY_HANGING     5   // anchored at the top: bottom vertices move
#define SWAY_UNDERWATER  6
#define SWAY_FLOAT       7   // bob on the water surface
#define SWAY_STALK       8   // the whole column leans a little
#define SWAY_WALL        9   // small flutter in place
#define SWAY_CROP        10  // like PLANT, smaller

// ---- decoding
// mc_Entity.x is a float holding the integer id (-1 when unmapped).
int welkinIdFromAttr(float mcEntityX) { return int(floor(mcEntityX + 0.5)); }

bool welkinIsBlockId(int id) { return id >= WELKIN_BLOCK_BASE && id < WELKIN_BLOCK_BASE + 1600; }
int welkinMat(int id) {
    if (!welkinIsBlockId(id)) return M_GENERIC;
    int r = id - WELKIN_BLOCK_BASE;
    return r - (r / 100) * 100;
}
int welkinLight(int id) {
    if (!welkinIsBlockId(id)) return L_NONE;
    return (id - WELKIN_BLOCK_BASE) / 100;
}
int welkinEntity(int entityIdValue) {
    int e = entityIdValue - WELKIN_ENTITY_BASE;
    return (e > 0 && e < 100) ? e : E_GENERIC;
}

int  matBits(int m)    { return WELKIN_MAT_BITS[clamp(m, 0, 99)]; }
int  matSway(int m)    { return matBits(m) & MATB_SWAY_MASK; }
int  matRough(int m)   { return (matBits(m) >> MATB_ROUGH_SHIFT) & 3; }
int  matPorous(int m)  { return (matBits(m) >> MATB_POROUS_SHIFT) & 3; }
bool matHas(int m, int flag) { return (matBits(m) & flag) != 0; }

// ---- family tests (use these instead of == on a single class)
bool matIsLeaves(int m)  { return m == M_LEAVES_OTHER || m == M_LEAVES_SEASONAL || m == M_LEAVES_NEEDLE; }
bool matIsFoliage(int m) { return matHas(m, MATF_FOLIAGE) || m == M_WATER_PLANT || m == M_FUNGUS; }
bool matIsWater(int m)   { return m == M_WATER; }
bool matIsIce(int m)     { return m == M_ICE; }
bool matIsGlassLike(int m) { return m == M_GLASS || m == M_GLASS_STAINED || m == M_ICE || m == M_GEL; }
bool matIsSnow(int m)    { return m == M_SNOW; }
bool matIsGround(int m)  { return m == M_GRASS_BLOCK || m == M_SOIL || m == M_SAND || m == M_GRAVEL || m == M_MOSS
                               || m == M_STONE || m == M_CLAY; }

// The skeleton's coarse MAT_* for a fine class, for code that still switches on those nine.
int welkinCoarseMat(int m) {
    if (m <= 9) return m;
    if (matIsLeaves(m)) return 2;                                   // MAT_LEAVES
    if (matIsFoliage(m) || m == M_GRASS_BLOCK || m == M_CACTUS) return 1; // MAT_FOLIAGE
    if (matIsGlassLike(m)) return 5;                                // MAT_GLASS
    if (m == M_GRAVEL) return 7;                                    // MAT_SAND
    if (m == M_PORTAL || m == M_FIRE || m == M_TORCH) return 4;     // MAT_EMISSIVE
    return 8;                                                       // MAT_STONE
}

// Light emission of the block being drawn, 0..1, from Iris's at_midBlock.w (0..15).
// Terrain programs only; block entities, the hand and DH have no at_midBlock: pass 15 there when L != 0.
float welkinEmission(float midBlockW) { return clamp(midBlockW, 0.0, 15.0) / 15.0; }

// ---- light colours: one flat colour per class, natural and a little warm.
vec3 welkinLightColour(int l) {
    if (l == L_FLAME)     return vec3(1.00, 0.62, 0.30);
    if (l == L_SOUL)      return vec3(0.35, 0.80, 0.95);
    if (l == L_REDSTONE)  return vec3(1.00, 0.28, 0.14);
    if (l == L_LAMP_WARM) return vec3(1.00, 0.80, 0.52);
    if (l == L_LAMP_COOL) return vec3(0.72, 0.90, 1.00);
    if (l == L_LAVA)      return vec3(1.00, 0.45, 0.12);
    if (l == L_AMETHYST)  return vec3(0.72, 0.46, 1.00);
    if (l == L_COPPER)    return vec3(0.48, 1.00, 0.58);
    if (l == L_CAVE_TEAL) return vec3(0.30, 0.95, 0.85);
    if (l == L_GLOWBERRY) return vec3(1.00, 0.74, 0.32);
    if (l == L_AMBER)     return vec3(1.00, 0.60, 0.20);
    if (l == L_PRISMITE)  return vec3(1.00, 0.72, 0.92);
    if (l == L_SCULK)     return vec3(0.26, 0.76, 0.86);
    if (l == L_END)       return vec3(0.86, 0.80, 1.00);
    return vec3(1.00, 0.80, 0.56);  // L_GENERIC and unknown: the skeleton's block-light colour
}

// Which texels of an emitting block glow, 0 or 1 (hard edges, no bloom). FULLGLOW classes glow whole;
// everything else glows only on its bright or saturated pixels (flames, ore specks, lantern panes, berries).
float welkinGlowMask(int m, int l, vec3 albedo, float emission) {
    if (l == L_NONE || emission <= 0.0) return 0.0;
    if (matHas(m, MATF_FULLGLOW)) return 1.0;
    float hi = max(albedo.r, max(albedo.g, albedo.b));
    float lo = min(albedo.r, min(albedo.g, albedo.b));
    if (m == M_ORE) return step(0.42, hi - lo);             // saturated specks in grey rock
    if (m == M_GEM || m == M_SPELEOTHEM) return step(0.55, hi);
    return step(0.72, hi);
}

// The emission value the skeleton writes to colortex2.z: two flat levels, so a dim block (a candle, an amethyst
// bud) glows less than a torch. 0 where the mask is off.
float welkinGlow(int m, int l, vec3 albedo, float emission) {
    float mask = welkinGlowMask(m, l, albedo, emission);
    return mask * (emission > 0.6 ? 0.85 : 0.5);
}

// ---- Distant Horizons / VOID LOD: dhMaterialId (DH_BLOCK_*) -> material class
int welkinDhMat(int dh) {
    if (dh == 1)  return M_LEAVES_OTHER;   // DH_BLOCK_LEAVES (far crowns are never bare)
    if (dh == 2)  return M_STONE;          // DH_BLOCK_STONE
    if (dh == 3)  return M_WOOD;           // DH_BLOCK_WOOD
    if (dh == 4)  return M_METAL;          // DH_BLOCK_METAL
    if (dh == 5)  return M_SOIL;           // DH_BLOCK_DIRT
    if (dh == 6)  return M_LAVA;           // DH_BLOCK_LAVA
    if (dh == 7)  return M_STONE;          // DH_BLOCK_DEEPSLATE
    if (dh == 8)  return M_SNOW;           // DH_BLOCK_SNOW
    if (dh == 9)  return M_SAND;           // DH_BLOCK_SAND
    if (dh == 10) return M_CLAY;           // DH_BLOCK_TERRACOTTA
    if (dh == 11) return M_STONE;          // DH_BLOCK_NETHER_STONE
    if (dh == 12) return M_WATER;          // DH_BLOCK_WATER
    if (dh == 13) return M_GRASS_BLOCK;    // DH_BLOCK_GRASS
    if (dh == 14) return M_INVISIBLE;      // DH_BLOCK_AIR
    if (dh == 15) return M_LAMP;           // DH_BLOCK_ILLUMINATED
    return M_GENERIC;                      // DH_BLOCK_UNKNOWN
}
int welkinDhLight(int dh) { return dh == 6 ? L_LAVA : (dh == 15 ? L_GENERIC : L_NONE); }

// ---- debug views (DEBUG_VIEW 8 material classes, 9 light colours)
vec3 welkinMatDebugColour(int m) {
    if (m == M_GENERIC) return vec3(0.5);
    float h = fract(float(m) * 0.6180339);
    vec3 c = clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    float v = m < 10 ? 1.0 : (m < 30 ? 0.85 : (m < 40 ? 0.7 : 0.55)); // brightness by group
    return c * v;
}
vec3 welkinLightDebugColour(int l, float glow) {
    return l == L_NONE ? vec3(0.08) : welkinLightColour(l) * (0.35 + 0.65 * glow);
}

#endif
