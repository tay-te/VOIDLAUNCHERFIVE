// VOID Welkin, atmos (welkin-atmos, shafts and bloom step, 2026-10-16): composite4, the bloom's gather (lib/bloom.glsl).
// Half resolution, into colortex9 (free after the ambient occlusion's passes). The bright source (composite3,
// colortex14) is read from six of its mip levels, each through a 3x3 tent at that level's own texel size (smooth, never
// a box), and summed with weights that add to 1 (energy-conserving: the halo spreads the bright light, it never makes
// more of it): a tight core from the finer levels, a wide soft halo from the coarser. final lays it on before its
// shoulder. Written from scratch for Welkin; no other pack's code.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FRAGMENT_SHADER

const bool colortex14MipmapEnabled = true;

in vec2 texcoord;

#ifndef WU_colortex14
#define WU_colortex14
uniform sampler2D colortex14;
#endif

/* RENDERTARGETS: 9 */
layout(location = 0) out vec4 outGlow;

// (Look D5) Each level through a cubic B-spline over its own 4x4 texels, fetched exactly (texelFetch). The mip levels
// are read without filtering between texels, so the old tent (four taps half a texel off) drew each coarse level as
// nested hard-edged boxes: round the sun the halo showed as a cream rectangle with an outline. The B-spline is smooth in
// value, slope and curve, and its weights add to 1 (each level stays energy-conserving)
vec3 tentAt(vec2 uv, float lod) {
	int l = int(lod);
	ivec2 sz = textureSize(colortex14, l);
	vec2 st = uv * vec2(sz) - 0.5;
	vec2 i0 = floor(st);
	vec2 f = st - i0;
	vec2 f2 = f * f;
	vec2 f3 = f2 * f;
	vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
	vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
	vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
	vec2 w3 = f3 / 6.0;
	vec4 wx = vec4(w0.x, w1.x, w2.x, w3.x);
	vec4 wy = vec4(w0.y, w1.y, w2.y, w3.y);
	ivec2 b = ivec2(i0) - 1;
	ivec2 hi = sz - 1;
	vec3 c = vec3(0.0);
	for (int y = 0; y < 4; y++) {
		vec3 row = vec3(0.0);
		int ty = clamp(b.y + y, 0, hi.y);
		for (int x = 0; x < 4; x++) {
			row += wx[x] * texelFetch(colortex14, ivec2(clamp(b.x + x, 0, hi.x), ty), l).rgb;
		}
		c += wy[y] * row;
	}
	return c;
}

// (review round 1) the widest levels a little less saturated: a saturated light's broad halo (the amber realm's
// crystals) spread as a red fringe round it; the core keeps the light's own colour
vec3 wideTint(vec3 c, float k) {
	return mix(c, vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), k);
}

// (look2) a broad level's light toward a warm amber at its own luminance, by k
vec3 wideWarm(vec3 c, float k) {
	float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
	vec3 amber = vec3(1.16, 0.98, 0.74);
	amber /= dot(amber, vec3(0.2126, 0.7152, 0.0722));
	return mix(c, amber * y, k);
}

void main() {
	vec3 g = vec3(0.0);
#ifdef BLOOM
	vec2 uv = gl_FragCoord.xy / vec2(textureSize(colortex14, 0));
	// the core (levels 1, 2) and the halo (3 .. 6); the weights sum to 1
	// (review round 1: a little more in the core and less in the widest level, a tighter halo)
	// (look2, 2026-10-18: wider and warmer, a painterly glow in the air: the weight moved toward the broad levels, and
	// the broad levels lean toward a warm amber instead of a neutral grey, so a low sun's, a lamp's or a fire's glow
	// spreads as warm light, never a grey wash; was 0.16 0.20 0.23 0.21 0.12 0.08, the wide levels greyed 0.25 and 0.4)
	g += tentAt(uv, 1.0) * 0.09;
	g += tentAt(uv, 2.0) * 0.13;
	g += tentAt(uv, 3.0) * 0.18;
	g += tentAt(uv, 4.0) * 0.21;
	g += wideWarm(tentAt(uv, 5.0), 0.30) * 0.21;
	g += wideWarm(tentAt(uv, 6.0), 0.45) * 0.18;
	// alpha: the frame's mean bright share (the 1x1 level), its luminance, for final's flat veil (lib/bloom.glsl
	// bloomApply; the merge of phase 2)
	ivec2 size = textureSize(colortex14, 0);
	vec3 mean = textureLod(colortex14, vec2(0.5), floor(log2(float(max(size.x, size.y))))).rgb;
	outGlow = vec4(g, dot(mean, vec3(0.2126, 0.7152, 0.0722)));
#else
	outGlow = vec4(g, 0.0);
#endif
}

#endif
