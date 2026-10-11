// VOID Welkin: where a screen pixel is, from the chunks' depth and the LOD's.
//
// Iris on 26.3 stores reversed depth but patches every read in the pack back to the usual convention: a read of
// depthtex* or dhDepthTex* returns 0 at the near plane and 1 at the far plane, where nothing was drawn. The LOD's
// depth (dhDepthTex0) is in its own perspective (dhProjection: near 16, far past its reach), so each depth goes back
// through its own inverse projection. Where the chunks drew, theirs is the surface (their colour is on screen);
// elsewhere the LOD's.

#ifndef WELKIN_DEPTH
#define WELKIN_DEPTH

#ifndef WU_depthtex0
#define WU_depthtex0
uniform sampler2D depthtex0;
#endif
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhDepthTex0
#define WU_dhDepthTex0
uniform sampler2D dhDepthTex0;
#endif
#ifndef WU_dhProjectionInverse
#define WU_dhProjectionInverse
uniform mat4 dhProjectionInverse;
#endif
#endif
// define WELKIN_DEPTH_BEHIND before including for surfaceBehind (composite passes only: depthtex1, dhDepthTex1)
#ifdef WELKIN_DEPTH_BEHIND
#ifndef WU_depthtex1
#define WU_depthtex1
uniform sampler2D depthtex1;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhDepthTex1
#define WU_dhDepthTex1
uniform sampler2D dhDepthTex1;
#endif
#endif
#endif

vec3 viewFromDepth(vec2 uv, float depth, mat4 inverseProjection) {
	vec4 p = inverseProjection * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
	return p.xyz / p.w;
}

struct Surface {
	vec3 view;      // view-space position
	float depth;    // the chosen depth, in its own projection
	bool sky;       // nothing drawn here, by either
	bool fromLod;   // the surface is the LOD's
	bool chunk;     // the chunks drew here
	bool lod;       // the LOD drew here
	bool lodNearer; // both drew, and the LOD's surface stands in front of the chunks' (the join's overlap)
};

// The surface at screen position `uv` from a chunk depth `d0` and an LOD depth `dh` (1: nothing drawn).
Surface pickSurface(float d0, float dh, vec2 uv) {
	Surface s;
	s.chunk = d0 < 1.0;
	s.depth = d0;
	s.view = viewFromDepth(uv, d0, gbufferProjectionInverse);
	s.fromLod = false;
	s.lod = false;
	s.lodNearer = false;
#ifdef DISTANT_HORIZONS
	s.lod = dh < 1.0;
	if (s.lod) {
		vec3 v = viewFromDepth(uv, dh, dhProjectionInverse);
		s.lodNearer = s.chunk && -v.z < -s.view.z;
		// The chunks draw after the LOD into the same colour buffers, so wherever they drew, the colour on screen is
		// theirs; their surface is then the one to light and fog, even where the LOD's (coarser) surface stands a
		// little in front of it along the join. The LOD's surface is used where the chunks drew nothing.
		if (!s.chunk) {
			s.view = v;
			s.depth = dh;
			s.fromLod = true;
		}
	}
#endif
	s.sky = !s.chunk && !s.lod;
	return s;
}

// The nearest surface at screen texel `p` (uv its centre), translucents included once they have drawn.
Surface surfaceAt(ivec2 p, vec2 uv) {
	float dh = 1.0;
#ifdef DISTANT_HORIZONS
	dh = texelFetch(dhDepthTex0, p, 0).r;
#endif
	return pickSurface(texelFetch(depthtex0, p, 0).r, dh, uv);
}

#ifdef WELKIN_DEPTH_BEHIND
// The nearest opaque surface at `p`: what is behind the water and glass (depthtex1, dhDepthTex1).
Surface surfaceBehind(ivec2 p, vec2 uv) {
	float dh = 1.0;
#ifdef DISTANT_HORIZONS
	dh = texelFetch(dhDepthTex1, p, 0).r;
#endif
	return pickSurface(texelFetch(depthtex1, p, 0).r, dh, uv);
}
#endif

#endif
