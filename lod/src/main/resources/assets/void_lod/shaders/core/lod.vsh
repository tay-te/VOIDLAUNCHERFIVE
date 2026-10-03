#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <void_lod:lodinfo.glsl>

// Per vertex: x and z in cells from the tile's corner, y in blocks, face (0 up, 2/3 north/south,
// 4/5 west/east, +8 for water). Per tile (one instance each): its corner in blocks and its level.
layout(location = 0) in ivec4 LodPos;
layout(location = 1) in vec4 LodColor;
layout(location = 2) in ivec4 LodTile;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 horizontal;

void main() {
    // Integer arithmetic until the camera is subtracted, so the far edge of the world keeps its precision.
    int cell = 1 << LodTile.z;
    ivec3 rel = ivec3(LodTile.x + LodPos.x * cell, LodPos.y, LodTile.y + LodPos.z * cell) - CameraBlockPos;
    vec3 pos = vec3(rel) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    int face = LodPos.w & 7;
    // Minecraft's own directional shading: tops full, north and south a little darker, east and west more.
    float shade = face == 0 ? 1.0 : (face <= 3 ? 0.8 : 0.62);
    // Full sky light, through the lightmap: the LOD darkens at dusk and night exactly as the chunks do.
    vec4 daylight = sample_lightmap(Sampler2, ivec2(0, 240));
    vertexColor = vec4(LodColor.rgb * shade, 1.0) * vec4(daylight.rgb, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    horizontal = pos.xz;
}
