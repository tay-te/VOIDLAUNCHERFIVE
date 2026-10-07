#ifndef GLOAM_FARCLOUDS_GLSL
#define GLOAM_FARCLOUDS_GLSL

// The cloud deck's shape, shared by the deck (far_clouds) and its shadows on the far land (far_shade).

// The fronts over the far view (WeatherMap: the front in red, in the fronts' drifting frame), and four tiling noises
// (CloudNoise) the heaps are shaped with.
uniform sampler2D WeatherMap;
uniform sampler2D CloudNoise;

// How much light a block of cloud at full density stops: a heap 40 blocks through is all but opaque, its frayed edges
// let the sky through.
const float SIGMA = 0.075;

// The clouds over one place: the column they fill, from bottom to top in world y; how much of a heap it is (h: 0 clear
// sky .. 1 the heart of a heap or a full overcast), the storm in it, and what the fronts make of the sky there.
struct Column {
    float bottom;
    float top;
    float h;
    float storm;
};

Column column(vec2 rel) {
    float f = texture(WeatherMap, (FarMap.xy + rel) * FarMap.z).r;
    vec3 w = farWeather(f);
    // a few fair-weather heaps even under a clear front: a summer sky is rarely empty
    float cover = max(w.x, 0.1);
    vec2 p = FarNoise.xy + rel;
    // heaps a few hundred blocks across, gathered into fields and streets several kilometres long
    float cluster = texture(CloudNoise, p / 8192.0).r;
    float heap = texture(CloudNoise, p / 1024.0).g * 0.65 + texture(CloudNoise, p / 256.0).b * 0.35;
    float n = clamp((heap - 0.5) * 2.6 + (cluster - 0.5) * 1.2 + 0.5, 0.0, 1.0);
    Column c;
    // the cover decides how much of the noise is cloud: a little under a clear sky, all of it under an overcast
    c.h = smoothstep(1.0 - cover, 1.0 - cover + 0.16, n);
    c.storm = w.y * c.h;
    // fair-weather heaps are flat; a fuller sky builds them up; a full overcast flattens into a deck; storms tower
    float thick = 28.0 + 120.0 * smoothstep(0.1, 0.7, cover);
    thick = mix(thick, 75.0, 0.6 * smoothstep(0.88, 1.0, cover));
    // the wettest parts of a front build towering heaps, and its storms tower highest of all
    float tower = max(w.y, 0.7 * smoothstep(0.76, 0.96, f));
    thick = mix(thick, max(FarLayer.y, thick), tower);
    // every heap its own height, so a field of them has a ragged skyline and an overcast a lumpy top
    float tops = texture(CloudNoise, p / 512.0).a;
    float base = FarLayer.x - 90.0 * w.y;
    // flat bottoms that lift a little toward a heap's thin edges
    c.bottom = base + thick * 0.1 * (1.0 - c.h);
    c.top = base + thick * c.h * (0.5 + 0.5 * tops);
    return c;
}

// How dense the cloud is at height y in column c over rel, and how far up the column y is (0 bottom .. 1 top).
float density(Column c, float y, vec2 rel, out float frac) {
    frac = 0.0;
    if (c.h <= 0.002 || y < c.bottom || y > c.top) {
        return 0.0;
    }
    frac = (y - c.bottom) / max(c.top - c.bottom, 1.0);
    // frayed, billowing edges: two fine noises leaning different ways with height, standing in for a 3D one
    vec2 p = FarNoise.xy + rel;
    float e = 0.5 * (texture(CloudNoise, (p + vec2(y * 1.7, y * 0.6)) / 96.0).a + texture(CloudNoise, (p * 1.3 - vec2(y * 0.8, y * 1.9)) / 128.0).r);
    float d = smoothstep(0.0, 0.25, c.h - (1.0 - e) * 0.35);
    return d * smoothstep(0.0, 0.06, frac) * smoothstep(1.0, 0.7, frac);
}

#endif
