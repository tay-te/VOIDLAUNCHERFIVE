#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

#include <gloam:farclouds.glsl>

// The cloud deck at half the screen's resolution, into a picture of its own (premultiplied colour and coverage), which
// far_composite lays over the world at full resolution, blurring away what grain the march leaves.
layout(location = 0) in vec4 nearPoint;

layout(location = 0) out vec4 fragColor;

// Henyey-Greenstein: how much light a cloud scatters at an angle to the sun, g its forward lean.
float phase(float cosine, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * 3.14159265 * pow(1.0 + g2 - 2.0 * g * cosine, 1.5));
}

// White noise from the pixel: the march's offset (a structured dither drew fine stripes over far towers, where a
// stride is wider than the details); the composite's blur takes most of the grain out.
float dither(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    vec3 dir = normalize(nearPoint.xyz / nearPoint.w);
    float cameraY = FarCamera.x;
    float reach = FarCamera.w;
    float low = FarLayer.x - 95.0;
    float high = FarLayer.x + FarLayer.y;
    float t0, t1;
    if (abs(dir.y) < 1e-5) {
        if (cameraY < low || cameraY > high) {
            fragColor = vec4(0.0);
            return;
        }
        t0 = 0.0;
        t1 = reach;
    } else {
        float a = (low - cameraY) / dir.y, b = (high - cameraY) / dir.y;
        t0 = max(min(a, b), 0.0);
        t1 = min(max(a, b), reach);
    }
    if (t1 <= t0) {
        fragColor = vec4(0.0);
        return;
    }

    // The light: the sun's colour (vanilla's cloud colour, warmed by a low sun), and the sky's light from all round,
    // bluish-grey under the clouds and brighter over them.
    vec3 lit = FarCloudColor.rgb;
    float low_sun = FarSunrise.a * (1.0 - smoothstep(0.0, 0.5, FarSun.y));
    vec3 sun = mix(lit, FarSunrise.rgb * max(max(lit.r, lit.g), lit.b) * 1.3, low_sun * 0.75) * FarSun.w;
    vec3 skyLight = mix(FogColor.rgb, lit, 0.5);
    float cosine = dot(dir, FarSun.xyz);
    // a silver lining toward the sun, a little back-scatter away from it
    float scatter = mix(phase(cosine, 0.65), phase(cosine, -0.2), 0.35) * 4.0 * 3.14159265 * 0.55;
    vec3 toSun = normalize(FarSun.xyz + vec3(0.0, 0.08, 0.0));

    float transmit = 1.0;
    vec3 light = vec3(0.0);
    float weighted = 0.0;
#ifdef FAR_FAST
    // Vanilla's fast clouds: one soft layer through the middle of the deck.
    float mid = FarLayer.x + 40.0;
    float tm = abs(dir.y) < 1e-5 ? -1.0 : (mid - cameraY) / dir.y;
    if (tm > 0.0 && tm < reach) {
        vec2 rel = dir.xz * tm;
        vec4 s = skyAt(rel);
        float height;
        float d = cloud(rel, deckBase(s) + 0.4 * deckDepth(s), s, false, height);
        float a = smoothstep(0.0, 0.35, d) * 0.92;
        light = mix(sun * 0.6 + skyLight * 0.55, FogColor.rgb, (1.0 - exp(-tm / FarAnchor.w)) * 0.8) * a;
        weighted = tm * a;
        transmit = 1.0 - a;
    }
#else
    // Long strides through clear air, short ones in cloud, stepping back on meeting one so its near edge is not skipped.
    // the pixel's own offset into every stride, so neighbouring pixels sample between each other's samples (kept when
    // the march steps back into a cloud: re-gridding there drew the clouds' insides as contour lines)
    float jitter = dither(gl_FragCoord.xy);
    float t = t0 + max(20.0, t0 * 0.03) * jitter;
    bool inside = false;
    int misses = 0;
    float chord = t1 - t0;
    for (int i = 0; i < 96 && t < t1; i++) {
        // (never so short that the march runs out of steps before the far side of the slab: a storm 12 km off stands
        // at the end of a long chord)
        float stride = inside ? max(clamp(t * 0.016, 8.0, 160.0), chord / 160.0) : max(clamp(t * 0.022, 16.0, 220.0), chord / 70.0);
        vec3 p = dir * t;
        float y = cameraY + p.y;
        vec4 s = skyAt(p.xz);
        float height;
        bool near = t < 3000.0;
        // a clear bubble round the camera: clouds thin away as it flies into them
        float d = cloud(p.xz, y, s, near, height) * smoothstep(24.0, 96.0, t);
        if (d > 0.002) {
            if (!inside) {
                inside = true;
                t = max(t0, t - stride + clamp(t * 0.016, 8.0, 160.0) * jitter);
                continue;
            }
            misses = 0;
            // the sun's light reaching this point: one sample of the cloud a way toward it (the sky there taken as here's)
            vec3 q = p + toSun * 50.0;
            float hq;
            float toward = cloud(q.xz, cameraY + q.y, s, false, hq) * 100.0;
            // and the cloud above shading its own underside
            float beer = exp(-SIGMA * 0.8 * toward) * mix(0.35, 1.0, smoothstep(0.0, 0.7, height));
            // the dark of a cloud's inside lifts toward its edges (light scattered many times)
            float powder = 1.0 - exp(-2.0 * SIGMA * d * 40.0);
            float shadeBase = mix(0.55, 0.3, s.y);
            vec3 ambient = skyLight * mix(shadeBase, 1.0, smoothstep(0.0, 1.0, height));
            vec3 col = sun * beer * mix(1.0, scatter, 0.6) * (0.5 + 0.5 * powder) * 1.2 + ambient * 0.62;
            float a = 1.0 - exp(-SIGMA * d * stride);
            // Aerial perspective, sample by sample: the air between pales a far cloud into the horizon's colour, so a
            // tower 10 km off is a bluish-grey mass rather than white (worked out per sample, a ray that slips through
            // a near wisp to a far cloud does not turn into a bright speck)
            col = mix(col, FogColor.rgb, (1.0 - exp(-t / FarAnchor.w)) * 0.8);
            light += transmit * a * col;
            weighted += transmit * a * t;
            transmit *= 1.0 - a;
            if (transmit < 0.02) {
                break;
            }
        } else if (inside && ++misses > 2) {
            inside = false;
        }
        t += stride;
    }
#endif
    float alpha = 1.0 - transmit;
    if (alpha < 0.003) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 color = light / alpha;
    // the deck fades out before the far air's edge
    float dist = weighted / alpha;
    alpha *= (1.0 - smoothstep(0.7 * reach, reach, dist)) * min(1.0, FarCloudColor.a * 1.25);
    fragColor = vec4(color * alpha, alpha);
}
