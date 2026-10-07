#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

// The cloud deck's half-resolution picture (far_clouds), premultiplied.
uniform sampler2D FarClouds;

layout(location = 0) in vec4 nearPoint;

layout(location = 0) out vec4 fragColor;

// Lays the deck's half-resolution picture over the world at full resolution: four filtered taps around the pixel (a
// tent over the half-size texels, which takes out the march's dither), at the depth where the ray enters the cloud
// layer, so land in front of the deck covers it at full sharpness and land under it does not.
void main() {
    vec2 size = vec2(textureSize(FarClouds, 0));
    vec2 uv = gl_FragCoord.xy / (size * 2.0);
    vec2 o = 0.5 / size;
    vec4 c = 0.25 * (texture(FarClouds, uv + vec2(-o.x, -o.y)) + texture(FarClouds, uv + vec2(o.x, -o.y))
        + texture(FarClouds, uv + vec2(-o.x, o.y)) + texture(FarClouds, uv + vec2(o.x, o.y)));
    if (c.a < 0.003) {
        discard;
    }
    vec3 dir = normalize(nearPoint.xyz / nearPoint.w);
    float cameraY = FarCamera.x;
    float low = FarLayer.x - 95.0, high = FarLayer.x + FarLayer.y;
    float t0 = 96.0;
    if (abs(dir.y) > 1e-5) {
        float a = (low - cameraY) / dir.y, b = (high - cameraY) / dir.y;
        t0 = max(min(a, b), 96.0);
    }
    vec4 clip = ProjMat * FarModelView * vec4(dir * t0, 1.0);
    fragColor = c;
    gl_FragDepth = clamp(clip.z / clip.w, 0.0, 1.0);
}
