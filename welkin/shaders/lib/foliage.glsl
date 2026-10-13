// VOID Welkin: foliage light (rebuild workstream C, 2026-10-13). The leaf and plant rules of the light ramp, moved here
// out of lib/light.glsl (which includes this file after sunFacing): how a leaf or a plant takes the sun (sunFacingMat),
// the light through leaves and plants (welkinGlow), the shade under a canopy (canopyShade, canopyLeak), the far land's
// leaves (farCanopySun, farCanopyFill), plants' fill from above (lightFillUp) and the thin-quad test (matThin).

#ifndef WELKIN_FOLIAGE
#define WELKIN_FOLIAGE

// Sunlight that filters down through a canopy or bounces in under an overhang: a share of the direct light's colour,
// with no direction, in the shade of places partly open to the sky (sky light about 6..13: under leaves, beneath a
// roof's edge). Out in the open shade (sky light 15) it is 0, so a house's or a cliff's shadow keeps its contrast; in a
// cave it is 0. It only fills the part of the face the sun does not light already.
// With the shadow map on, the sun's own patches through the gaps carry the light under a wood, so the leak is less
// (the floor's shade stays deep between the patches).
float canopyLeak(float skyLevel) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	const float leak = 0.035;
#else
	const float leak = 0.12;
#endif
	return leak * smoothstep(0.25, 0.55, skyLevel) * (1.0 - smoothstep(0.84, 0.97, skyLevel));
}

// How deep a place lies under a canopy or an overhang by day, 0..1: partly open to the sky (sky light about 9..13) and
// out of the sun. There the sky's fill is cut further (round 1: the forest floor between the sun patches, and a
// crown's inner leaves, sink into deep, readable shade, about sRGB 10..12 at their darkest, instead of an even mid
// green), while the patches the sun reaches keep it. In the open (sky light 15) and in caves (the ramp is dark
// already) it is 0; by night it fades out, so moonlit woods keep their level.
float canopyShade(float skyLevel, float sunReach) {
#ifdef WORLD_OVERWORLD
	float under = smoothstep(0.35, 0.6, skyLevel) * (1.0 - smoothstep(0.82, 0.97, skyLevel));
	return under * (1.0 - clamp(sunReach, 0.0, 1.0)) * smoothstep(-0.02, 0.12, sunHeight());
#else
	return 0.0;
#endif
}

// The same for a material (plumbing's class, M_*): a plant's crossed quads (double-sided, their normal means little)
// take the light on both sides, more where a quad faces it. A leaf block is a cube of small leaves turned every way,
// so every face takes a good share of the sun where the light reaches it (the shadow map, softened, decides that:
// inner faces of a crown sit in their neighbours' shade), the faces turned to it most; the shaded faces also get
// the light that comes through the leaves (welkinGlow).
float sunFacingMat(vec3 n, vec3 l, int m) {
	float ndl = dot(n, l);
	if (matHas(m, MATF_SSS)) {
		if (matIsLeaves(m)) {
			// (round 3: half the light on any face, not 0.36: a crown's sides read sunlit green where the map lets the
			// sun reach them, as a cube of leaves turned every way does)
			return 0.5 + 0.5 * sunFacing(ndl);
		}
		return 0.62 + 0.38 * abs(ndl);
	}
	return sunFacing(ndl);
}

// The light that comes through leaves and plants toward the eye, as a share of the direct light (0 for everything
// else): a leaf's face turned from the sun glows where the sun stands behind it, most when the eye looks toward the
// sun (a forward lobe; a little from every side, as a leaf scatters), and a grass blade or a flower lit from behind
// glows the same way. through: what the leaves between the sun and the face let pass, with the sky-light gate
// (lib/shadow.glsl welkinSunReachEx). v the direction from the eye to the point, n the face's world normal, l the
// light's.
float welkinGlow(vec3 v, vec3 n, vec3 l, int m, float through) {
	if (!matHas(m, MATF_SSS)) {
		return 0.0;
	}
	// (round 3: what passes is a share, never more than all of it; and the moon's light through leaves is a faint
	// silver rim, not a glow)
	through = clamp(through, 0.0, 1.0) * (sunAngle < 0.5 ? 1.0 : 0.35);
	// round 1: a rim, not a glow: mostly when the eye looks toward the light (a narrow forward lobe), at most half the
	// direct light, so back-lit crowns read as warm-rimmed silhouettes instead of neon-lit ones
	float c = clamp(dot(v, l), 0.0, 1.0);
	float c2 = c * c;
	float lobe = 0.06 + 0.94 * c2 * c2;
	if (matIsLeaves(m)) {
		// at a low sun the light through a crown spreads wider (the long path through the leaves scatters it), so a
		// crown seen from the side glows gold at dawn and at the golden hour, as the sun's colour makes it
		float low = 1.0 - smoothstep(0.06, 0.3, l.y);
		lobe = mix(lobe, 0.4 + 0.6 * c2, low);
		// the faces turned away from the light (half on an edge-on face)
		return 0.7 * lobe * through * clamp(0.5 - dot(n, l), 0.0, 1.0);
	}
	return 0.32 * lobe * through;
}

// How far the sky's fill takes a face as facing up, 0..1 (round 3: deferred sets it for plants' crossed quads, whose
// normal means little: a grass blade or a flower sees the open sky above it, so in the shade it reads as light as the
// ground's top, not as dark as a wall; the land's shade then shows its tops and tufts against the dark sides).
float lightFillUp = 0.0;

// Whether a material is a plant's thin quad (for the shadow lookup, which then steps toward the light).
bool matThin(int m) {
	return matHas(m, MATF_SSS) && !matIsLeaves(m);
}

// What the far land's leaves (the LOD's, and the near chunks' past the shadow map) take of the sun and the sky's fill
// while the near shadow map is on: a crown's own shade, averaged. Near crowns shade their own lower leaves and the
// leaves under them, the far flat tops cannot, so without this the far forest is a brighter, greener band than the
// near forest in front of it. 1 for everything else, and with the shadow map off (then neither has self-shade).
float farCanopySun(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? 0.56 : 1.0;
#else
	return 1.0;
#endif
}

float farCanopyFill(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? 0.76 : 1.0;
#else
	return 1.0;
#endif
}
#endif
