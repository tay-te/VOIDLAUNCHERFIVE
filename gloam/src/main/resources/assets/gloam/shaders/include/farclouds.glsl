#ifndef GLOAM_FARCLOUDS_GLSL
#define GLOAM_FARCLOUDS_GLSL

// The cloud deck as a volume, shared by the deck (far_clouds) and its shadows on the far land (far_shade).

// The fronts over the far view (WeatherMap: the front in red, in the fronts' drifting frame), and the 3D noise the clouds
// are shaped with (CloudVolume: its 64 slices in an 8 x 8 grid, each with a wrapping border; red the billowy shape,
// green, blue and alpha cellular noise at three finer scales for the edges).
uniform sampler2D WeatherMap;
uniform sampler2D CloudVolume;

// How much light a block of cloud at full density stops.
const float SIGMA = 0.06;
// The noise's tile in blocks: the shape (heaps a few hundred blocks across) and the detail (billows of tens).
const float SHAPE_TILE = 2048.0;
const float DETAIL_TILE = 512.0;

// One slice-blended sample of the 3D noise at p, in tiles (it wraps every tile).
vec4 volume(vec3 p) {
    vec3 q = fract(p) * 64.0;
    float z = q.z - 0.5;
    float z0 = floor(z);
    float t = z - z0;
    int s0 = int(mod(z0, 64.0));
    int s1 = int(mod(z0 + 1.0, 64.0));
    vec2 inCell = q.xy + 1.0;
    vec2 c0 = vec2(float(s0 % 8), float(s0 / 8)) * 66.0, c1 = vec2(float(s1 % 8), float(s1 / 8)) * 66.0;
    vec4 a = textureLod(CloudVolume, (c0 + inCell) / 528.0, 0.0);
    vec4 b = textureLod(CloudVolume, (c1 + inCell) / 528.0, 0.0);
    return mix(a, b, t);
}

float remap01(float v, float lo, float hi) {
    return clamp((v - lo) / max(hi - lo, 1e-4), 0.0, 1.0);
}

// What the sky over rel holds: x the cover (a little even under a clear front), y the storm, z how much the heaps
// tower (showers and storms), w the front itself.
vec4 skyAt(vec2 rel) {
    float f = texture(WeatherMap, (FarMap.xy + rel) * FarMap.z).r;
    vec3 w = farWeather(f);
    return vec4(max(w.x, 0.12), w.y, max(w.y, smoothstep(0.74, 0.95, f)), f);
}

// The deck's base over a place with sky s: storms bring theirs lower.
float deckBase(vec4 s) {
    return FarLayer.x - 90.0 * s.y;
}

// How deep the clouds over a place can reach above its base: fair-weather heaps shallow, a fuller sky building them up,
// a full overcast a flattish deck, showers and storms towering.
float deckDepth(vec4 s) {
    float depth = 70.0 + 110.0 * smoothstep(0.1, 0.7, s.x);
    depth = mix(depth, 95.0, 0.5 * smoothstep(0.88, 1.0, s.x));
    return mix(depth, max(FarLayer.y, depth), s.z);
}

// The billows at the last point cloud() eroded (0..1), for the deck's shading: the hollows between billows darker.
float cloudBillows = 0.5;

// The cloud's density at a point (rel across the ground from the camera, y its height), and how high in its cloud the
// point is (0 base .. 1 top); detail adds the eroding billows near the camera (far off, one coarser sample frays the
// outline instead, so a distant bank seen edge-on breaks into heaps rather than a smooth sausage); light skips both.
float cloud(vec2 rel, float y, vec4 s, bool detail, bool far, out float height) {
    float base = deckBase(s);
    float depth = deckDepth(s);
    if (y < base - 1.0 || y > FarLayer.x + FarLayer.y) {
        height = 0.0;
        return 0.0;
    }
    vec2 p = FarNoise.xy + rel;
    // the shape noise first: where it is strong the cloud reaches higher, so heads billow up in heaps and towers rise
    // in rounded turrets instead of to one flat lid
    vec4 n = volume(vec3(p.x, y * 2.0, p.y) / SHAPE_TILE);
    // the billowy shape, and the same sample's cellular noise breaking a long bank into separate heaps
    float shape = n.r * 0.58 + n.g * 0.42;
    height = (y - base) / (depth * mix(0.6, 1.2, shape));
    // A storm's anvil: a flat sheet spreading at the top of the towers, wider than the tower under it (the front's
    // wetter ring), so the column flares out at its head.
    float anvilBase = FarLayer.x + 0.72 * FarLayer.y, anvilDepth = 0.16 * FarLayer.y;
    float ah = (y - anvilBase) / anvilDepth;
    float anvil = 0.0;
    if (ah > 0.0 && ah < 1.0) {
        anvil = smoothstep(0.82, 0.9, s.w) * smoothstep(0.0, 0.25, ah) * smoothstep(1.0, 0.6, ah);
        height = max(height, 0.9 + 0.1 * ah);
    }
    if ((height <= 0.0 || height >= 1.0) && anvil <= 0.0) {
        return 0.0;
    }
    // The height profile: a quick flat-bottomed start, a rounded head; a tower keeps its girth higher up.
    float hh = clamp(height, 0.0, 1.0);
    float profile = smoothstep(0.0, 0.08, hh) * smoothstep(1.0, mix(0.5, 0.82, s.z), hh);
    // the cover decides how much of the shape noise is cloud: some of its tops under a clear front, all of it under an
    // overcast; and a cloud's girth narrows toward its head
    float coverage = max(s.x * profile, anvil);
    float d = remap01(shape, 1.0 - coverage, 1.0) * max(profile, anvil);
    if (detail && d > 0.0) {
        // erode the edges into billows: the detail noise rising with height, so bases are lumpy and heads round
        vec4 e = volume(vec3(p.x, y * 1.6, p.y) / DETAIL_TILE + vec3(FarCamera.y * 0.002, 0.0, 0.0));
        float billows = e.g * 0.625 + e.b * 0.25 + e.a * 0.125;
        cloudBillows = billows;
        float erode = mix(1.0 - billows, billows, smoothstep(0.1, 0.6, hh));
        d = remap01(d, erode * 0.34, 1.0);
    } else if (far && d > 0.0 && d < 0.6) {
        vec4 e = volume(vec3(p.x, y * 1.2, p.y) / (DETAIL_TILE * 3.0));
        cloudBillows = e.g;
        d = remap01(d, (1.0 - e.g) * 0.35, 1.0);
    }
    return d;
}

#endif
