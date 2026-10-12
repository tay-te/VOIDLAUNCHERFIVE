// VOID Welkin: render targets. Iris reads these directives from the program sources that include this file
// (deferred, composite, final).
//
// colortex0  RGBA16F  the scene: albedo from the deferred-lit gbuffers, then the lit colour from deferred on
// colortex1  RGBA16   octahedral world normal (xy), light map levels (z block, w sky)
// colortex2  RGBA8    x: 1 = lit in deferred, y: material id / 255, z: emission level and light class
//                     (lib/common.glsl packEmitClass), w: Minecraft's corner shading (separateAo; 1 for none)
// colortex3  RGBA8    water in front (gbuffers_water, dh_water): rgb its lit colour, a 1; 0 elsewhere
// colortex4  RGBA8    water and shadow info (water_shadows); cleared to 0
// colortex5  RGBA16   the sky table, 64x32 (prepare; lib/sky/table.glsl reads it); never cleared, rewritten each frame
//
// Programs that light themselves (entities, hand, particles, lines) write colortex2.x = 0 (blending off for
// colortex2 in shaders.properties), so whichever order Iris draws them in, deferred never lights a pixel twice.

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16;
const int colortex2Format = RGBA8;
const int colortex3Format = RGBA8;
const vec4 colortex1ClearColor = vec4(0.5, 0.5, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex1Clear = true;
const bool colortex2Clear = true;
const vec4 colortex3ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex3Clear = true;
const int colortex4Format = RGBA8;
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex4Clear = true;
const int colortex5Format = RGBA16;
const bool colortex5Clear = false;
*/
