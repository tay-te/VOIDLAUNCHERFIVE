#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

#include <gloam:farclouds.glsl>

layout(location = 0) in vec4 nearPoint;

layout(location = 0) out vec4 fragColor;

// The light a bit of cloud sends toward the camera: the top in vanilla's cloud colour, the underside grey (a storm's
// near black), the sun's side of the sky glowing at dawn and dusk, and a bright rim looking toward the sun.
vec3 shade(Column c, float frac, vec3 dir) {
    vec3 lit = FarCloudColor.rgb;
    float under = mix(0.55, 0.26, c.storm);
    under *= mix(1.0, 0.7, smoothstep(60.0, 300.0, c.top - c.bottom));
    vec3 color = lit * mix(under, 1.0, smoothstep(0.0, 1.0, frac));
    float sunward = max(dot(normalize(dir.xz + vec2(1e-4, 0.0)), vec2(sign(FarSun.x), 0.0)), 0.0);
    float glow = FarSunrise.a * (0.12 + 0.88 * sunward * sunward) * (1.0 - 0.5 * frac);
    color = mix(color, FarSunrise.rgb * max(max(lit.r, lit.g), lit.b) * 1.1, glow * 0.6);
    float rim = pow(max(dot(dir, FarSun.xyz), 0.0), 10.0);
    color += lit * FarSun.w * rim * 0.6 * (1.0 - 0.8 * c.h);
    return color;
}

// White noise from the pixel, for the march's jitter.
float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float depthOf(vec3 pos) {
    vec4 clip = ProjMat * FarModelView * vec4(pos, 1.0);
    return clamp(clip.z / clip.w, 0.0, 1.0);
}

void main() {
    vec3 dir = normalize(nearPoint.xyz / nearPoint.w);
    float cameraY = FarCamera.x;
    float reach = FarCamera.w;
    float low = FarLayer.x - 90.0;
    float high = FarLayer.x + FarLayer.y;
    // where the ray is between the lowest base and the highest top in view
    float t0, t1;
    if (abs(dir.y) < 1e-5) {
        if (cameraY < low || cameraY > high) {
            discard;
        }
        t0 = 0.0;
        t1 = reach;
    } else {
        float a = (low - cameraY) / dir.y, b = (high - cameraY) / dir.y;
        t0 = max(min(a, b), 0.0);
        t1 = min(max(a, b), reach);
    }
    if (t1 <= t0) {
        discard;
    }

    float transmit = 1.0;
    vec3 light = vec3(0.0);
    float hit = -1.0;
#ifdef FAR_FAST
    // Vanilla's fast clouds: one flat layer, soft-edged, in the middle of the deck.
    float mid = FarLayer.x + 20.0;
    float tm = abs(dir.y) < 1e-5 ? -1.0 : (mid - cameraY) / dir.y;
    if (tm <= 0.0 || tm > reach) {
        discard;
    }
    vec2 rel = dir.xz * tm;
    Column c = column(rel);
    float a = smoothstep(0.05, 0.45, c.h) * 0.92;
    light = shade(c, 0.6 * c.h, dir) * a;
    transmit = 1.0 - a;
    hit = tm;
#else
    // March through the slab: long strides through clear air, growing with distance, and short ones inside a cloud, as
    // short as a few pixels there, stepping back on meeting one so its near edge is not skipped. Jittered per pixel so
    // what is left of the steps blurs into fine noise rather than bands.
    float t = t0 + max(16.0, t0 * 0.06) * hash12(gl_FragCoord.xy);
    bool inside = false;
    int misses = 0;
    for (int i = 0; i < 88 && t < t1; i++) {
        float stride = inside ? clamp(t * 0.012, 6.0, 160.0) : max(16.0, t * 0.06);
        vec3 p = dir * t;
        Column c = column(p.xz);
        float frac;
        // a clear bubble round the camera: clouds thin away as it flies into them instead of standing at its face
        float d = density(c, cameraY + p.y, p.xz, frac) * smoothstep(24.0, 96.0, t);
        if (d > 0.001) {
            if (!inside) {
                inside = true;
                t = max(t0, t - stride * 0.8);
                continue;
            }
            misses = 0;
            float a = 1.0 - exp(-SIGMA * d * stride);
            if (hit < 0.0) {
                hit = t;
            }
            light += transmit * a * shade(c, frac, dir);
            transmit *= 1.0 - a;
            if (transmit < 0.01) {
                break;
            }
        } else if (inside && ++misses > 2) {
            // out the other side: back to long strides
            inside = false;
        }
        t += stride;
    }
#endif
    float alpha = 1.0 - transmit;
    if (hit < 0.0 || alpha < 0.004) {
        discard;
    }
    vec3 color = light / alpha;
    // the air between: far clouds pale into the horizon's colour, and fade out before the far air's edge
    float haze = 1.0 - exp(-hit / FarAnchor.w);
    color = mix(color, FogColor.rgb, haze * 0.85);
    alpha *= (1.0 - smoothstep(0.7 * reach, reach, hit)) * min(1.0, FarCloudColor.a * 1.25);
    fragColor = vec4(color * alpha, alpha);
    gl_FragDepth = depthOf(dir * hit);
}
