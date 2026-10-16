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
// colortex9  RGBA16F  half size: the ambient occlusion's raw term, bent normal and view depth (deferred_ao; lib/ao.glsl)
// colortex10 RGBA16F  the shadow's raw terms (deferred_shadow, run as deferred2): r the sun's reach, g what passes the
//                     leaves, b water travel (>= 0) or -1 - the canopy over the point (0..1, workstream C), a view
//                     depth (1e4: not lit in deferred)
// colortex11 RGBA16F  the resolved shadow (deferred_resolve, deferred3): r reach, g what passes, b debug, a view depth;
//                     never cleared (the next frame's history with TEMPORAL_LIGHT)
// colortex12 RGBA16F  half size: the resolved occlusion (deferred_aoresolve, deferred1, with AMBIENT_OCCLUSION): x
//                     visibility, yz the bent normal's tangent parts in the face's frame, a view depth (lib/ao.glsl)
// colortex13 RGBA16F  half size: the sun shafts' raw term (deferred_shafts, run as deferred5; workstream D): r the sunlit
//                     share of the near air along the view ray (noisy; composite2 resolves it), a the view distance
// colortex14 RGBA16F  half size: the bloom's bright source (composite3, mipmapped for composite4, whose gather goes into
//                     colortex9, free by then; atmos, welkin-atmos). composite3 also writes the sun beams into colortex12
//                     (free after the light pass), which final gathers
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
const int colortex10Format = RGBA16F;
const bool colortex10Clear = false;
const int colortex11Format = RGBA16F;
const bool colortex11Clear = false;
const int colortex12Format = RGBA16F;
const bool colortex12Clear = false;
const int colortex13Format = RGBA16F;
const bool colortex13Clear = false;
const int colortex14Format = RGBA16F;
const bool colortex14Clear = false;
*/
