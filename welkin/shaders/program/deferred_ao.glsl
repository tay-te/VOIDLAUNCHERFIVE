// VOID Welkin: deferred_ao, run as Iris's first deferred pass (tools/wrappers.sh maps it to deferred; the light runs
// after it). The ambient occlusion (lighting techniques 2.4, workstream B): ground-truth ambient occlusion (Jimenez,
// Wu, Pesce and Jarabo 2016, "Practical Real-Time Strategies for Accurate Indirect Occlusion") from the chunks' depth,
// at half the screen's resolution (size.buffer.colortex9 = 0.5 0.5), written raw to colortex9 for the light pass to
// resolve (lib/ao.glsl lightAoFetch, through lib/denoise.glsl's 4x4 resolve):
//
//   r   the visibility, 0 (fully hidden) .. 1 (the whole hemisphere open), cosine-weighted
//   g b the bent normal (the mean open direction) in the face's own frame (lib/ao.glsl aoFrame): its two tangent
//       components; the third, along the normal, is positive and follows from them, so a resolve can average them
//   a   the view depth (blocks), the resolve's plane test; 1e4 where nothing near was drawn
//
// For each pixel, AO_SLICES slices through the view direction, each turned by the pixel's lighting noise (lib/noise.glsl:
// tiled 4x4, so the resolve's 4x4 window sees sixteen turns, one smooth many-slice result with no grain), and in each
// slice the highest horizon on both sides within AO_RADIUS blocks (AO_STEPS depth reads a side, spaced closer near the
// pixel, their start offset by the second noise value), falling off over the radius' outer 40 % and with a thickness
// guess (what stands well in front of the point along the view counts as thinner), integrated against the cosine.
// Leaves look wider (a crown's heart sinks into its own shade); plants' crossed quads are taken as facing up (a tuft
// shades its own root, the tip stays open). Past a few dozen blocks the radius grows with the distance (never under
// AO_WIDE_PX on screen), and the LOD's land (dhDepthTex0, where the chunks drew nothing) takes it as well, so the middle
// and far land get occlusion at their own scale: hollows, steps, gullies, the floor of a far wood. Out by AO_FAR.

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
#include "/lib/noise.glsl"

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
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
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
layout(location = 0) out vec4 outAo;

// the view position at screen position uv of the surface lit there (the chunks' where they drew, else the LOD's, as
// lib/depth.glsl pickSurface chooses), from full-resolution pixel f's depths, or z = 0 where nothing was drawn
vec3 aoSurfaceAt(ivec2 f, vec2 uv) {
	float d = texelFetch(depthtex0, f, 0).r;
	if (d < 1.0) {
		return viewFromDepth(uv, d, gbufferProjectionInverse);
	}
#ifdef DISTANT_HORIZONS
	float dh = texelFetch(dhDepthTex0, f, 0).r;
	if (dh < 1.0) {
		return viewFromDepth(uv, dh, dhProjectionInverse);
	}
#endif
	return vec3(0.0);
}

vec3 aoViewAt(ivec2 f, vec2 size) {
	return aoSurfaceAt(f, (vec2(f) + 0.5) / size);
}

// The slices' summed visibility and bent normal (view space) for one noise value (GTAO, Jimenez et al. 2016): the
// horizons on both sides of each slice, the cosine-weighted arc between them, and the slice's bent normal.
void aoSlices(vec2 noise, vec3 P, vec3 N, vec3 V, vec2 size, float radiusPx, float falloffMul, float falloffAdd,
		out float vis, out vec3 bent) {
	vis = 0.0;
	bent = vec3(0.0);
	for (int s = 0; s < AO_SLICES; s++) {
		float phi = (float(s) + noise.x) * (3.1415927 / float(AO_SLICES));
		vec2 omega = vec2(cos(phi), sin(phi));
		// the slice's plane: through the view direction and omega (screen x right, y up = view x, y)
		vec3 dirV = vec3(omega, 0.0);
		vec3 ortho = normalize(dirV - dot(dirV, V) * V);
		vec3 axis = cross(ortho, V);
		vec3 projN = N - axis * dot(N, axis);
		float projLen = length(projN);
		float cosN = clamp(dot(projN, V) / max(projLen, 1e-4), 0.0, 1.0);
		float n = (dot(ortho, projN) < 0.0 ? -1.0 : 1.0) * acos(cosN);
		// the lowest a horizon can be on each side: the face's own plane
		float lowCos0 = cos(n + 1.5707963);
		float lowCos1 = cos(n - 1.5707963);
		float hc0 = lowCos0;
		float hc1 = lowCos1;
		for (int j = 0; j < AO_STEPS; j++) {
			float t = (float(j) + noise.y) / float(AO_STEPS);
			float off = t * t * radiusPx + 1.3; // closer near the pixel; never the pixel itself
			for (int side = 0; side < 2; side++) {
				vec2 o = omega * (side == 0 ? off : -off);
				ivec2 f = ivec2(texcoord * size + o);
				if (any(lessThan(f, ivec2(0))) || any(greaterThanEqual(f, ivec2(size)))) {
					continue;
				}
				vec3 S = aoViewAt(f, size);
				if (S.z == 0.0) {
					continue;
				}
				vec3 delta = S - P;
				float dist = length(delta);
				// the thickness guess: a sample that stands out in front of the point along the view is thinner than it
				// looks (a post before a wall), so it counts as farther off
				float distT = length(delta + V * (max(dot(delta, V), 0.0) * AO_THICKNESS));
				float w = clamp(distT * falloffMul + falloffAdd, 0.0, 1.0);
				float shc = dot(delta / max(dist, 1e-5), V);
				if (side == 0) {
					hc0 = max(hc0, mix(lowCos0, shc, w));
				} else {
					hc1 = max(hc1, mix(lowCos1, shc, w));
				}
			}
		}
		projLen = mix(projLen, 1.0, 0.05);
		float h0 = -acos(clamp(hc1, -1.0, 1.0));
		float h1 = acos(clamp(hc0, -1.0, 1.0));
		h0 = n + clamp(h0 - n, -1.5707963, 1.5707963);
		h1 = n + clamp(h1 - n, -1.5707963, 1.5707963);
		float sinN = sin(n);
		float arc0 = (cosN + 2.0 * h0 * sinN - cos(2.0 * h0 - n)) * 0.25;
		float arc1 = (cosN + 2.0 * h1 * sinN - cos(2.0 * h1 - n)) * 0.25;
		vis += projLen * (arc0 + arc1);
		// the slice's bent normal (the paper's algorithm 2): t0 along ortho, t1 toward the eye
		float t0 = (6.0 * sin(h0 - n) - sin(3.0 * h0 - n) + 6.0 * sin(h1 - n) - sin(3.0 * h1 - n)
			+ 16.0 * sinN - 3.0 * (sin(h0 + n) + sin(h1 + n))) / 12.0;
		float t1 = (-cos(3.0 * h0 - n) - cos(3.0 * h1 - n) + 8.0 * cosN - 3.0 * (cos(h0 + n) + cos(h1 + n))) / 12.0;
		bent += (ortho * t0 + V * t1) * projLen;
	}
}

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy); // the half-resolution texel
	vec2 size = vec2(viewWidth, viewHeight);
	ivec2 full = ivec2(texcoord * size); // the full-resolution pixel under it
	// (the position at the texel's centre, as lib/denoise.glsl's resolve places it; the LOD's land too, so the middle
	// and far land take the wide occlusion)
	vec3 P = aoSurfaceAt(full, texcoord);
	float z = -P.z;
	if (P.z == 0.0 || z > AO_FAR) {
		outAo = vec4(1.0, 0.0, 0.0, P.z == 0.0 ? 6e4 : min(z, 6e4));
		return;
	}
	vec4 flags = texelFetch(colortex2, full, 0);
	int m = int(flags.y * 255.0 + 0.5);
	if (flags.x < 0.5) {
		// self-lit things: none
		outAo = vec4(1.0, 0.0, 0.0, min(z, 6e4));
		return;
	}
	bool plant = matHas(m, MATF_SSS) && !matIsLeaves(m);
	vec3 nW = plant ? vec3(0.0, 1.0, 0.0) : octDecode(texelFetch(colortex1, full, 0).xy);
	vec3 N = normalize(mat3(gbufferModelView) * nW);
	vec3 V = normalize(-P);

	// the radius: wider in a crown, narrower in a tuft; on screen, in full-resolution pixels, capped
	float radius = matIsLeaves(m) ? AO_RADIUS_LEAVES : (plant ? AO_RADIUS_PLANT : AO_RADIUS);
	// (and never under AO_WIDE_PX on screen: past a few dozen blocks the radius grows with the distance, so the middle
	// and far land, the LOD's too, take an occlusion at their own scale: the hollows, terrace steps and gullies of a
	// hillside at a hundred blocks, the floor between a far wood's crowns, a valley's sides a kilometre off)
	float pxPerBlock = gbufferProjection[1][1] * 0.5 * size.y / max(z, 0.05);
	float radiusPx = clamp(radius * pxPerBlock, AO_WIDE_PX * size.y / 900.0, AO_MAX_PX);
	radius = radiusPx / pxPerBlock;
	if (radiusPx < 2.0) {
		outAo = vec4(1.0, 0.0, 0.0, z);
		return;
	}
	float falloffRange = 0.4 * radius;
	float falloffMul = -1.0 / falloffRange;
	float falloffAdd = (radius - falloffRange) / falloffRange + 1.0;

	float vis;
	vec3 bent;
#if AO_REFERENCE > 0
	// the noise-free reference for the grain check: the average of all sixteen noise values of the tile, per pixel
	vis = 0.0;
	bent = vec3(0.0);
	for (int k = 0; k < 16; k++) {
		float vk;
		vec3 bk;
		aoSlices(r2(float(k)), P, N, V, size, radiusPx, falloffMul, falloffAdd, vk, bk);
		vis += vk;
		bent += bk;
	}
	vis *= 1.0 / 16.0;
	bent *= 1.0 / 16.0;
#else
	aoSlices(lightNoise2(p, lightFrame()), P, N, V, size, radiusPx, falloffMul, falloffAdd, vis, bent);
#endif
	vis = clamp(vis / float(AO_SLICES), 0.0, 1.0);
	// the bent normal in world space, then in the face's own frame
	vec3 bW = mat3(gbufferModelViewInverse) * bent;
	float bl = length(bW);
	bW = bl > 1e-4 ? bW / bl : nW;
	if (dot(bW, nW) < 0.05) {
		bW = normalize(bW + nW * (0.05 - dot(bW, nW)));
	}
	mat3 fr = aoFrame(nW);
	outAo = vec4(vis, dot(bW, fr[0]), dot(bW, fr[1]), min(z, 6e4));
}

#endif
