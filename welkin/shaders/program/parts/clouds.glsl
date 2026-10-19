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

#include "/lib/hdr.glsl"
#include "/lib/clouds/envflat.glsl"

out vec2 texcoord;
// (Look D3) the sky's horizon (away, toward the sun) and zenith in scene light, once (lib/clouds/shade.glsl wcDomeScene)
flat out vec3 wcSkyHorAwayS;
flat out vec3 wcSkyHorSunS;
flat out vec3 wcSkyZenS;
// (Look D6) the veil inside and near a deck outside the dusk (lib/clouds/shade.glsl wcVeilColour), in scene light, once
flat out vec3 wcVeilS;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
	wcSkyHorAwayS = sceneFromAuthored(skyHorizon(false));
	wcSkyHorSunS = sceneFromAuthored(skyHorizon(true));
	wcSkyZenS = sceneFromAuthored(skyZenith());
	// (as wcAlbedo(0) and wcShoulder in lib/clouds/shade.glsl)
	vec3 veil = vec3(0.97, 0.97, 0.98) * (skyAmbientLight() * 0.82 + skyDirectLight() * 0.42);
	veil = mix(veil, 0.8 + 0.2 * (1.0 - exp(-(veil - 0.8) * 5.0)), step(0.8, veil));
	wcVeilS = sceneFromAuthored(veil);
	// (D7) the clouds' frame and weather, once (lib/clouds/envflat.glsl)
	wcEnvWrite(welkinCloudEnv());
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"
// the clouds rework (2026-10-16): this pass reads the raw map's smooth fields for the light inside the clouds
#define WC_FIELD
#define WC_SKY_FLAT
#include "/lib/clouds/shade.glsl"
#include "/lib/hdr.glsl"
#include "/lib/air.glsl"
#include "/lib/clouds/envflat.glsl"
#include "/lib/clouds/hitbuf.glsl"

in vec2 texcoord;

#ifndef WU_colortex10
#define WU_colortex10
uniform sampler2D colortex10; // (D7) the march's hit (program/deferred_cloudmarch.glsl, lib/clouds/hitbuf.glsl)
#endif

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
	// (HDR stage B) all in scene light: the clouds' light (lib/clouds/shade.glsl) over the scene by their cover
	float km = WC_NO_CLOUD_KM;
	float cloudT = 1.0; // (HDR stage C) what of the sky behind the clouds still shows, for the light shafts
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
		outCloud = vec4(km, 1.0, 0.0, 0.0);
		return;
	}

	CloudEnv e = wcEnvRead(); // (D7) worked out in the vertex shader
	// where each deck turns into a far band (lib/clouds/shade.glsl wcBandOf): past a deck that is all band the march
	// has nothing left to draw, so it stops there. (D7) Only the cut before the march (wcBandCut, the same arithmetic),
	// the bands after it: held through the march's loop they cost registers
	float tCut = min(tScene, min(wcBandCut(0, ro, rd, pixAngle, e), min(wcBandCut(1, ro, rd, pixAngle, e),
		wcBandCut(2, ro, rd, pixAngle, e))));
	// (D7) the march ran in its own pass (deferred6) against the opaque land, never nearer than tScene here: the same
	// steps, kept only nearer than this pass's cut
	CloudHit h = wcHitDecode(texelFetch(colortex10, p, 0), rd, e);
	if (h.hit && h.t >= tCut) {
		h.hit = false;
		h.sheet = false;
		h.t = 1e30;
	}
	steps = vec3(h.steps, h.gapCols, h.jumps);
	vec4 band0 = wcBandOf(0, ro, rd, pixAngle, tScene, e);
	vec4 band1 = wcBandOf(1, ro, rd, pixAngle, tScene, e);
	vec4 band2 = wcBandOf(2, ro, rd, pixAngle, tScene, e);
	// a deck near the eye's height is its fog bank instead (wcSlabFog): its band would draw the deck's far edge as a
	// hard line on the horizon over the fog
	band0.z *= 1.0 - wcNearDeck(0, e);
	band1.z *= 1.0 - wcNearDeck(1, e);
	band2.z *= 1.0 - wcNearDeck(2, e);
	// the far bands, the farthest first; each lies over what is behind it (a gap's sky, or the cell hit inside it)
	if (band0.x < band1.x) { vec4 t = band0; band0 = band1; band1 = t; }
	if (band1.x < band2.x) { vec4 t = band1; band1 = band2; band2 = t; }
	if (band0.x < band1.x) { vec4 t = band0; band0 = band1; band1 = t; }
	// (clouds2) one cloud light a pixel: the hit's own, or, in a gap, the nearest band drawn here; the bands lay that
	// colour on (over a hit, its own: a hit lit as the band stood as flat strips with hard edges over the horizon).
	// Two copies of the cloud light (the hit's and the band's) had cost a tenth of a millisecond, and three band lights
	// a pixel more.
	vec4 sel = vec4(1e30, 1e30, 0.0, 0.0);
	for (int i = 0; i < 3; i++) {
		vec4 b = i == 0 ? band0 : (i == 1 ? band1 : band2);
		if (b.z > 0.0 && b.x < tScene && (!h.hit || h.t >= b.x - 1.0) && b.x < sel.x) {
			sel = b;
		}
	}
	bool useBand = sel.z > 0.0 && !h.hit;
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
		cloudT *= 1.0 - opacity;
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
			cloudT *= 1.0 - clamp(alpha, 0.0, 1.0);
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
		// (Look D6) once, not per deck; and laid on as the land's layered air is (lib/air.glsl airLayered): a deck at the
		// eye's height seen edge-on to the horizon was a milky cream band the far land dissolved in (the mesa vista); it
		// is a blue-grey bank away from the sun, warm toward it, a step under the band's light
		vec3 veil = airLayered(wcVeilColour(rd, e), rd, e.sun);
		for (int k = 0; k < 3; k++) {
			float f = wcSlabFog(k, rd, seen, wcDeckShare(k, wHere, e), e);
			if (f > 0.0) {
				color.rgb = veil * f + color.rgb * (1.0 - f);
				cloudT *= 1.0 - clamp(f, 0.0, 1.0);
			}
		}
	}
	if (h.inside) {
		color.rgb = welkinCloudVeil(color.rgb, rd, min(h.tOut, tScene), e);
		cloudT *= exp(-min(h.tOut, tScene) / 7.0);
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
	outCloud = vec4(km, cloudT, 0.0, 0.0); // colortex6 is RG16F: the clouds' distance and transmittance
}

#endif
