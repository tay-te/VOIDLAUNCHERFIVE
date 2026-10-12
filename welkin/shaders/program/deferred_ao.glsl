// VOID Welkin: deferred_ao, run as Iris's first deferred pass (tools/wrappers.sh maps it to deferred; the light is
// deferred1). Contact occlusion: how much of the open space round a near surface point is taken up by what stands
// beside it, from the chunks' depth, written to colortex9 (r the raw term, g the view distance) for deferred1 to blur
// and lay on the sky's fill (lib/ao.glsl). It darkens the inside corners Minecraft's own corner shading misses (a
// wall's foot behind a fence, under a stair, round a lantern) and gives plants and bushes a small darker pool at
// their base.
//
// At half the screen's resolution (size.buffer.colortex9 = 0.5 0.5: the pass draws a quarter of the pixels). Four
// samples a texel on a disc in the face's plane, turned and scaled by the texel's place in a 4x4 tile; deferred1
// averages each 4x4 tile back (lib/ao.glsl), so the sixteen turns add up to one smooth 64-sample result with no
// pattern and no noise, the same every frame. Only within about 72 blocks (the term is a fraction of a block wide).

#define WELKIN_AO_PASS
#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/ao.glsl"
#include "/lib/depth.glsl"

in vec2 texcoord;

#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 9 */
layout(location = 0) out vec2 outAo;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy); // the half-resolution texel
	vec2 size = vec2(viewWidth, viewHeight);
	ivec2 full = ivec2(texcoord * size); // the full-resolution pixel under it
	float d = texelFetch(depthtex0, full, 0).r;
	vec3 v = viewFromDepth(texcoord, d, gbufferProjectionInverse);
	float z = -v.z;
	if (d >= 1.0 || z > AO_FAR) {
		outAo = vec2(1.0, d >= 1.0 ? 1e4 : z);
		return;
	}
	vec4 flags = texelFetch(colortex2, full, 0);
	int m = int(flags.y * 255.0 + 0.5);
	if (flags.x < 0.5 || (matHas(m, MATF_SSS) && !matIsLeaves(m))) {
		// self-lit things and plants' own quads (their normal means little): none on them
		outAo = vec2(1.0, z);
		return;
	}
	vec3 n = normalize(mat3(gbufferModelView) * octDecode(texelFetch(colortex1, full, 0).xy));
	// a basis on the face
	vec3 t = normalize(abs(n.y) < 0.9 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
	vec3 b = cross(n, t);
	// the pixel's place in its 4x4 tile turns the four samples (a quarter turn apart) and sets their radii
	int k = (p.x & 3) + 4 * (p.y & 3);
	float turn = (float(k) + 0.5) * (1.5707963 / 16.0);
	float scale = fract(float(k) * 0.618034 + 0.31);
	vec3 o = v + n * (0.02 + 0.002 * z); // off the face, so the face itself never counts
	float occ = 0.0;
	for (int i = 0; i < 4; i++) {
		float a = turn + float(i) * 1.5707963;
		// radii spread over 0.15..1 of AO_RADIUS, a different spread for each sample
		float r = AO_RADIUS * (0.15 + 0.85 * fract(scale + float(i) * 0.25));
		vec3 s = o + (t * cos(a) + b * sin(a)) * r;
		vec4 c = gbufferProjection * vec4(s, 1.0);
		vec2 uv = c.xy / c.w * 0.5 + 0.5;
		if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
			continue;
		}
		float ds = texelFetch(depthtex0, ivec2(uv * size), 0).r;
		float zs = -viewFromDepth(uv, ds, gbufferProjectionInverse).z;
		// something stands in front of the sample, within the radius of the point: it hides that part of the space
		float front = -s.z - zs;
		occ += smoothstep(0.02 + 0.002 * z, 0.08 + 0.004 * z, front) * (1.0 - smoothstep(AO_RADIUS, 2.0 * AO_RADIUS, front));
	}
	// leaves crowd each other's faces everywhere in a crown: half the term there, or the crowns go grimy
	outAo = vec2(1.0 - occ * (matIsLeaves(m) ? 0.125 : 0.25), z);
}

#endif
