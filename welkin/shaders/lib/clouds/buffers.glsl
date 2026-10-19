// VOID Welkin: the clouds' render targets. Iris reads these directives from any program source that includes this
// file; the clouds program does, and so must one program that always runs (deferred or final), so the target is
// still cleared when CLOUDS is off and the clouds program is disabled.
//
// colortex6  R16F  the distance to the cloud at each pixel, in kilometres; 1000 where there is none. The far weather
//                  (composite1) and the near atmosphere (composite2) read it: haze stops at the cloud (the clouds fade
//                  themselves by ring), rain curtains behind a nearer cloud are hidden.

// colortex7  RGBA8 160 x 1280  the raw cloud map (lib/clouds/map.glsl), written by the first prepare pass every frame;
//                  never cleared (every texel is rewritten). Sizes are set in shaders.properties (size.buffer.*).

// colortex8  RGBA16 160 x 1280 the finished cloud map (edges and blocks added), written by the second prepare pass
//                  every frame; what everything after reads.

/*
const int colortex8Format = RGBA16;
const bool colortex8Clear = false;
const int colortex7Format = RGBA8;
const bool colortex7Clear = false;
const int colortex6Format = RG16F;
const bool colortex6Clear = true;
const vec4 colortex6ClearColor = vec4(1000.0, 1.0, 0.0, 0.0);
*/

#ifndef WELKIN_CLOUD_BUFFERS
#define WELKIN_CLOUD_BUFFERS

#define WC_NO_CLOUD_KM 1000.0

#endif
