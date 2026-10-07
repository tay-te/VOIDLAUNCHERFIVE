#version 330
#extension GL_ARB_separate_shader_objects : require

#include <gloam:farinfo.glsl>
#include <gloam:farclouds.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// How much of the sun the land at rel gets through the clouds: the column the sun's ray crosses on its way down,
// direct light stopped by the cloud in it, the sky's light still there (a shadow under a heap is about half as bright as
// the sunlit field beside it).
float sunlight(vec2 rel) {
    float up = max(FarSun.y, 0.3);
    float mid = FarLayer.x + 50.0;
    vec2 at = rel + FarSun.xz / up * (mid - FarShade.w);
    Column c = column(at);
    if (c.h <= 0.002) {
        return 1.0;
    }
    float depth = max(c.top - c.bottom, 0.0) * smoothstep(0.0, 0.3, c.h) / up;
    float direct = exp(-SIGMA * 0.35 * depth);
    return mix(0.5, 1.0, direct);
}

// The far land's sunlight under the clouds, against the light where the camera stands: the chunks around the camera
// keep vanilla's light, so a field in a cloud's shadow is darker than they are only if the camera stands in the sun,
// and a full overcast leaves the far land as bright as the near. Stored halved, as LodApi.setShade reads it.
void main() {
    vec2 rel = FarShade.xy + texCoord * FarShade.z;
    float here = sunlight(vec2(0.0));
    float light = sunlight(rel) / max(here, 0.05);
    fragColor = vec4(vec3(clamp(light * 0.5, 0.0, 1.0)), 1.0);
}
