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
    vec3 toSun = normalize(vec3(FarSun.x, up, FarSun.z));
    // four samples up the sun's ray through the deck, where it crosses the clouds over the ground
    float optical = 0.0;
    for (int k = 0; k < 4; k++) {
        float y = FarLayer.x - 60.0 + (float(k) + 0.5) * 70.0;
        vec2 at = rel + toSun.xz / toSun.y * (y - FarShade.w);
        vec4 s = skyAt(at);
        float height;
        optical += cloud(at, y, s, false, height) * 70.0 / toSun.y;
    }
    return mix(0.5, 1.0, exp(-SIGMA * 0.5 * optical));
}

// The far land's sunlight under the clouds, against the light where the camera stands: the chunks around the camera
// keep vanilla's light, so a field in a cloud's shadow is darker than they are only if the camera stands in the sun,
// and a full overcast leaves the far land as bright as the near. Stored halved, as LodApi.setShade reads it.
void main() {
    vec2 rel = FarShade.xy + texCoord * FarShade.z;
    float here = sunlight(vec2(0.0));
    // (in a cloud's shadow itself, the camera's chunks are still lit as vanilla lights them: the far fields in the sun
    // are then hardly brighter than they are)
    float light = clamp(sunlight(rel) / max(here, 0.5), 0.5, 1.1);
    fragColor = vec4(vec3(clamp(light * 0.5, 0.0, 1.0)), 1.0);
}
