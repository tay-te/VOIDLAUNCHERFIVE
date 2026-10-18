// VOID Welkin: the voxel clouds (M3). The first composite pass: after the translucents (depthtex0 holds water and
// glass, so a lake below a deck is hidden by the deck and a pane in front of one shows it), before the atmosphere.
//
// Per pixel: the land's distance (the nearer of the chunks and the LOD, lib/depth.glsl), the view ray, then the
// bounded voxel DDA (lib/clouds/march.glsl). A hit is lit by the sky table's light (lib/clouds/shade.glsl) and written
// over the scene; its distance goes to colortex6 for the passes after. Overworld only (world0 wrappers). Debug views 10 (the
// cost map) and 11 (the rings) are drawn here (7 is M1's tiles, 8 and 9 plumbing's); the atmosphere pass's views overwrite 1..9.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"
// the clouds rework (2026-10-16): this pass reads the raw map's smooth fields for the light inside the clouds
#define WC_FIELD
#include "/lib/clouds/shade.glsl"

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 0,6 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outCloud;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
	float km = WC_NO_CLOUD_KM;
	vec3 steps = vec3(0.0); // columns and jumps the ray took, of them gap columns, jumps (the gltest cost map)
#ifdef CLOUDS
	Surface s = surfaceAt(p, texcoord);
	float tScene = s.sky ? 1e30 : length(s.view);
	vec3 rd = normalize(mat3(gbufferModelViewInverse) * viewFromDepth(texcoord, 1.0, gbufferProjectionInverse));
	vec3 ro = vec3(0.0); // deferred's eyePos convention: camera-relative, no view-bob translation
	float pixAngle = 2.0 / (gbufferProjection[1][1] * viewHeight);
	// (clouds2) land nearer than the lowest a cloud can stand, seen by an eye well under every deck: nothing to draw,
	// and nothing worked out (the weather, the bands, the fog banks); most of the land on screen ends here. Every
	// deck stands at or over vanilla's cloud height (Gloam's CloudGrid.Layers, lib/clouds/env.glsl wcLayersOf).
	float eyeY = float(cameraPositionInt.y) + cameraPositionFract.y;
	float lowest = cloudHeight - 1.0;
	if (eyeY < lowest - 40.0 && (rd.y <= 0.0 || tScene * rd.y < lowest - eyeY)) {
		outColor = color;
		outCloud = vec4(km, steps);
		return;
	}

	CloudEnv e = welkinCloudEnv();
	// where each deck turns into a far band (lib/clouds/shade.glsl wcBandOf): past a deck that is all band the march
	// has nothing left to draw, so it stops there
	vec4 band0 = wcBandOf(0, ro, rd, pixAngle, tScene, e);
	vec4 band1 = wcBandOf(1, ro, rd, pixAngle, tScene, e);
	vec4 band2 = wcBandOf(2, ro, rd, pixAngle, tScene, e);
	// a deck near the eye's height is its fog bank instead (wcSlabFog): its band would draw the deck's far edge as a
	// hard line on the horizon over the fog
	band0.z *= 1.0 - wcNearDeck(0, e);
	band1.z *= 1.0 - wcNearDeck(1, e);
	band2.z *= 1.0 - wcNearDeck(2, e);
	float tCut = tScene;
	tCut = band0.z >= 0.999 ? min(tCut, band0.x) : tCut;
	tCut = band1.z >= 0.999 ? min(tCut, band1.x) : tCut;
	tCut = band2.z >= 0.999 ? min(tCut, band2.x) : tCut;
	CloudHit h = welkinCloudMarch(ro, rd, tCut, pixAngle, e);
	steps = vec3(h.steps, h.gapCols, h.jumps);
	// the far bands, the farthest first; each lies over what is behind it (a gap's sky, or the cell hit inside it)
	if (band0.x < band1.x) { vec4 t = band0; band0 = band1; band1 = t; }
	if (band1.x < band2.x) { vec4 t = band1; band1 = band2; band2 = t; }
	if (band0.x < band1.x) { vec4 t = band0; band0 = band1; band1 = t; }
	// (clouds2) one cloud light a pixel: the hit's own, or the nearest band drawn here (a hit the band has mostly
	// taken over is lit as the band, so it matches the gaps beside it); the bands lay that colour on. Two copies of
	// the cloud light (the hit's and the band's) had cost a tenth of a millisecond, and three band lights a pixel more.
	vec4 sel = vec4(1e30, 1e30, 0.0, 0.0);
	for (int i = 0; i < 3; i++) {
		vec4 b = i == 0 ? band0 : (i == 1 ? band1 : band2);
		if (b.z > 0.0 && b.x < tScene && (!h.hit || h.t >= b.x - 1.0) && b.x < sel.x) {
			sel = b;
		}
	}
	bool useBand = sel.z > 0.0 && (!h.hit || sel.z >= 0.5);
	vec3 lit = vec3(0.0);
	if (h.hit || sel.z > 0.0) {
		lit = welkinCloudShade(useBand ? wcBandHit(rd, sel.y, e) : h, ro, rd, e, pixAngle, useBand);
	}
	float opacity = 1.0;
	if (h.hit) {
		vec3 cc = lit;
		// a deck at eye height: its cells give way to the fog bank below (wcSlabFog) out to a few kilometres, where
		// the bank covers them (seen edge-on, the far cells of a thin bank stood as hard dark lines on the horizon)
		int k = wcDeckAt(e.camY + rd.y * h.t - h.n.y * 0.01, e);
		opacity = k >= 0 ? mix(1.0, smoothstep(300.0, 3000.0, h.t), wcNearDeck(k, e)) : 1.0;
		color.rgb = mix(color.rgb, cc, opacity);
		km = opacity > 0.5 ? h.t / 1000.0 : km;
	}
	for (int i = 0; i < 3; i++) {
		vec4 b = i == 0 ? band0 : (i == 1 ? band1 : band2);
		if (b.z > 0.0 && b.x < tScene && (!h.hit || h.t >= b.x - 1.0)) {
			// a cell hit inside the band keeps more of its body than a gap: the band only softens it toward the sky
			bool cell = h.hit && h.t <= b.y + (b.y - b.x) + 1.0;
			// (air and tone (D): the band lays the deck's cloud over what lies behind it, by how much of the deck is
			// cloud; its gaps show that, not the horizon's colour. Over land past a deck below the eye the horizon
			// colour had painted out the far land along a hard line, with the far ridges standing over it unhazed. A
			// pixel counts as cloud for the haze only where the band is mostly cloud.)
			float alpha = b.z * (cell ? mix(b.w, 1.0, 0.6) : b.w);
			color.rgb = mix(color.rgb, lit, alpha);
			if (alpha > 0.5) {
				km = min(km, b.y / 1000.0);
			}
		}
	}
	// the decks near the eye's height as fog banks, as far as the eye sees: to the land, or to a cloud face that is
	// drawn whole (a near face given way to the fog must not cut the fog short, or its outline would show in it); in
	// front of everything else the clouds drew
	if (wcNearDeck(0, e) + wcNearDeck(1, e) + wcNearDeck(2, e) > 0.0) {
		vec4 wHere = wcWeatherAt(vec2(0.0), true, e);
		float seen = h.hit && opacity >= 0.99 ? h.t : tScene;
		for (int k = 0; k < 3; k++) {
			float f = wcSlabFog(k, rd, seen, wcDeckShare(k, wHere, e), e);
			if (f > 0.0) {
				color.rgb = welkinCloudVeil(color.rgb, rd, 1e30, e) * f + color.rgb * (1.0 - f);
			}
		}
	}
	if (h.inside) {
		color.rgb = welkinCloudVeil(color.rgb, rd, min(h.tOut, tScene), e);
	}
#if DEBUG_VIEW == 10
	// the cost map: columns and jumps per pixel, blue few .. red at the cap (WC_STEPS)
	float k = float(h.steps) / float(WC_STEPS);
	color.rgb = h.steps == 0 ? color.rgb * 0.3 : mix(mix(vec3(0.0, 0.2, 1.0), vec3(0.0, 1.0, 0.2), clamp(k * 2.0, 0.0, 1.0)),
		vec3(1.0, 0.0, 0.0), clamp(k * 2.0 - 1.0, 0.0, 1.0));
#elif DEBUG_VIEW == 11
	// the rings: each ring of cells its own colour, the sheets past the DDA white
	if (h.hit) {
		color.rgb = h.sheet ? vec3(1.0) : vec3(fract(float(h.level) * 0.37), fract(float(h.level) * 0.61 + 0.3), fract(float(h.level) * 0.83 + 0.6));
	}
#endif
#endif
	outColor = color;
	outCloud = vec4(km, steps); // colortex6 is R16F: only km is kept
}

#endif
