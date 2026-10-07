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

// The clouds over one place: the column they can fill, from bottom to top in world y, and what shapes them there: how
// much of a heap the place is by the smooth noise (h: 0 clear sky .. 1 the heart of a heap or a full overcast), the
// smooth noise itself and the cover, which the fine shape works from, the storm in it, and the deck's base.
struct Column {
    float bottom;
    float top;
    float h;
    float storm;
    float base;
    float shape;
    float cover;
};

Column column(vec2 rel) {
    float f = texture(WeatherMap, (FarMap.xy + rel) * FarMap.z).r;
    vec3 w = farWeather(f);
    Column c;
    // a few fair-weather heaps even under a clear front: a summer sky is rarely empty
    c.cover = max(w.x, 0.1);
    vec2 p = FarNoise.xy + rel;
    // heaps several hundred blocks across, gathered into fields and streets kilometres long
    float cluster = texture(CloudNoise, p / 8192.0).r;
    float heap = texture(CloudNoise, p / 1024.0).g;
    c.shape = clamp((heap - 0.5) * 2.2 + (cluster - 0.5) * 1.2 + 0.5, 0.0, 1.0);
    // the cover decides how much of the noise is cloud: a little under a clear sky, all of it under an overcast;
    // the outline in height is a dome over the smooth noise, so heaps are rounded and towers have rounded heads
    c.h = smoothstep(1.0 - c.cover - 0.05, 1.0 - c.cover + 0.35, c.shape);
    c.storm = w.y * c.h;
    // fair-weather heaps are flat; a fuller sky builds them up; a full overcast flattens into a deck; showers and
    // storms tower
    float thick = 28.0 + 120.0 * smoothstep(0.1, 0.7, c.cover);
    thick = mix(thick, 75.0, 0.6 * smoothstep(0.88, 1.0, c.cover));
    float tower = max(w.y, smoothstep(0.72, 0.94, f));
    thick = mix(thick, max(FarLayer.y, thick), tower);
    // every heap its own height, as the fields they gather in
    float tops = cluster;
    c.base = FarLayer.x - 90.0 * w.y;
    // flat bottoms that lift a little toward a heap's thin edges
    c.bottom = c.base + thick * 0.15 * (1.0 - c.h);
    c.top = c.base + thick * c.h * (0.6 + 0.4 * tops);
    return c;
}

// A 2D noise channel standing in for a 3D one: sampled in slices {@code step} blocks apart in height, each slice moved
// far across the texture from the last and blended between, so it changes with height as much as across. A noise
// that only depended on x and z would stand every heap up as a comb of vertical streaks.
float sliced(vec2 uv, float y, float step, int channel) {
    float slice = y / step;
    float s0 = floor(slice);
    vec2 a = fract(vec2(s0 * 0.371, s0 * 0.619)), b = fract(vec2(s0 * 0.371 + 0.371, s0 * 0.619 + 0.619));
    vec4 n0 = texture(CloudNoise, uv + a), n1 = texture(CloudNoise, uv + b);
    float t = smoothstep(0.0, 1.0, slice - s0);
    return channel == 2 ? mix(n0.b, n1.b, t) : mix(n0.a, n1.a, t);
}

// How dense the cloud is at height y in column c over rel, and how far up the column y is (0 bottom .. 1 top). Far off
// (coarse) a heap is its smooth dome; near, the fine noise breaks it into billows and frays its edges.
float density(Column c, float y, vec2 rel, bool coarse, out float frac) {
    frac = 0.0;
    if (c.h <= 0.002 || y < c.bottom || y > c.top) {
        return 0.0;
    }
    frac = (y - c.bottom) / max(c.top - c.bottom, 1.0);
    float profile = smoothstep(0.0, 0.06, frac) * smoothstep(1.0, 0.7, frac);
    if (coarse) {
        return smoothstep(0.05, 0.35, c.h) * profile;
    }
    vec2 p = FarNoise.xy + rel;
    float n = c.shape + (sliced(p / 256.0, y, 48.0, 2) - 0.5) * 0.9;
    float h = smoothstep(1.0 - c.cover, 1.0 - c.cover + 0.22, n);
    float e = sliced(p / 128.0, y, 20.0, 3);
    return smoothstep(0.0, 0.25, h - (1.0 - e) * 0.35) * profile;
}

#endif
