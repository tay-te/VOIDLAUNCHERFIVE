// VOID Welkin: foliage light (rebuild workstream C, 2026-10-13). The leaf and plant rules of the light ramp, moved here
// out of lib/light.glsl (which includes this file after sunFacing): how a leaf or a plant takes the sun (sunFacingMat),
// the light through leaves and plants (welkinGlow), the shade under a canopy (canopyShade, canopyLeak), the far land's
// leaves (farCanopySun, farCanopyFill), plants' fill from above (lightFillUp) and the thin-quad test (matThin).

#ifndef WELKIN_FOLIAGE
#define WELKIN_FOLIAGE

// How far a point stands under a canopy, 0..1, as program/deferred_shadow.glsl found it (lib/shadow.glsl shadowCanopy:
// leaves are the first thing the light meets before it and the sky light is cut) and the light pass set it, or -1
// where nobody looked (past the map, without it, forward programs): there the sky light stands in.
float foliageCanopy = -1.0;

// The share of the canopy the sky-light level says (the old key, where the map does not say): partly open to the sky
// (sky light about 9..13).
float canopyFromSky(float skyLevel) {
	return smoothstep(0.35, 0.6, skyLevel) * (1.0 - smoothstep(0.82, 0.97, skyLevel));
}

// Sunlight that filters down through a canopy or bounces in under an overhang: a share of the direct light's colour,
// with no direction, under leaves (or, without the map's word, in places partly open to the sky). Out in the open shade
// it is 0, so a house's or a cliff's shadow keeps its contrast; in a cave it is 0. It only fills the part of the face
// the sun does not light already. With the shadow map on, the sun's own flecks through the gaps carry the light under
// a wood, so the leak is faint (the floor's shade stays deep between the flecks).
float canopyLeak(float skyLevel) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	const float leak = 0.012;
#else
	const float leak = 0.12;
#endif
	float under = foliageCanopy >= 0.0 ? foliageCanopy * smoothstep(0.25, 0.55, skyLevel) : canopyFromSky(skyLevel);
	return leak * under;
}

// How deep a place lies under a canopy by day, 0..1: where the shadow map finds leaves just above it (workstream C:
// the sky-light key barely moved inside a crown, at sky light 13..14), else partly open to the sky. There the sky's
// fill is cut (CANOPY_FILL_CUT): the forest floor between the flecks and a crown's heart sink into deep, readable
// shade while its outer, sunlit shell stays bright. In caves it is 0 (the ramp is dark already); by night it fades
// out, so moonlit woods keep their level. sunReach: what the sun reaches; without the map's word, sunlit patches keep
// their fill (the old rule).
const float CANOPY_FILL_CUT = 0.8;
float canopyShade(float skyLevel, float sunReach) {
#ifdef WORLD_OVERWORLD
	float day = smoothstep(-0.02, 0.12, sunHeight());
	if (foliageCanopy >= 0.0) {
		return foliageCanopy * smoothstep(0.25, 0.5, skyLevel) * day;
	}
	return canopyFromSky(skyLevel) * (1.0 - clamp(sunReach, 0.0, 1.0)) * day;
#else
	return 0.0;
#endif
}

// The same for a material (plumbing's class, M_*): a plant's crossed quads (double-sided, their normal means little)
// take the light on both sides, more where a quad faces it. A leaf block is a cube of small leaves turned every way:
// its leaves face the sun from any face it reaches, so a crown's top under a grazing sun still takes about half of it
// (the leaves on it stand at every angle; plain Lambert gave a far wood's tops a tenth at 06:30 and the woods read as
// a dull carpet); the faces turned to the sun take it whole, and the faces turned away sink to LEAF_FLOOR (the
// leaves on them are in their own block's shade). Workstream C (2026-10-13): round 3's floor of a half on every face
// made each crown one even mid green. The shadow map decides where the light reaches; the shaded faces get the light
// through the leaves (welkinGlow).
const float LEAF_WRAP = 0.6;
const float LEAF_FLOOR = 0.15;
// How much more of Minecraft's face shading a leaf's fill keeps (program/deferred.glsl), so a crown's blocks read.
const float LEAF_FACE_SHADE = 0.5;
float leafFacing(float ndl) {
	return LEAF_FLOOR + (1.0 - LEAF_FLOOR) * clamp((ndl + LEAF_WRAP) / (1.0 + LEAF_WRAP), 0.0, 1.0);
}
float sunFacingMat(vec3 n, vec3 l, int m) {
	float ndl = dot(n, l);
	if (matHas(m, MATF_SSS)) {
		if (matIsLeaves(m)) {
			return leafFacing(ndl);
		}
		return 0.62 + 0.38 * abs(ndl);
	}
	return sunFacing(ndl);
}

// Henyey-Greenstein's phase (Henyey and Greenstein 1941) toward the eye for asymmetry g, scaled so the forward peak is
// 1: c the cosine between the eye's ray and the light's direction (1: looking straight into the light).
float leafPhase(float c, float g) {
	float k = 1.0 + g * g - 2.0 * g * c;
	return pow((1.0 - g) * (1.0 - g) / k, 1.5);
}

// The light that comes through leaves and plants toward the eye, as a share of the direct light (0 for everything
// else): a leaf's face turned from the sun glows where the sun stands behind it, most when the eye looks toward the
// sun, and a grass blade or a flower lit from behind glows the same way. through: what the leaves between the sun and
// the face let pass (for a leaf, per pixel: exp(-LEAF_SIGMA x the blocks of leaves at its own map texel, lib/shadow.glsl
// shadowLeafThrough), so the thin edges of a crown glow and its heart does not), with the sky-light gate. v the
// direction from the eye to the point, n the face's world normal, l the light's.
// Workstream C: the lobe is a Henyey-Greenstein forward lobe (g 0.6) plus 15 % from every side, as a leaf scatters
// (the translucent look of Barre-Brisebois and Bouchard, GDC 2011); at a low sun the long path through a crown spreads
// it wider (g 0.35). The colour stays the sun's times the leaf's at round 1's desaturation (lib/light.glsl): warm,
// never lime.
float welkinGlow(vec3 v, vec3 n, vec3 l, int m, float through) {
	if (!matHas(m, MATF_SSS)) {
		return 0.0;
	}
	// (round 3: what passes is a share, never more than all of it; and the moon's light through leaves is a faint
	// silver rim, not a glow)
	through = clamp(through, 0.0, 1.0) * (sunAngle < 0.5 ? 1.0 : 0.35);
	float c = dot(v, l);
	if (matIsLeaves(m)) {
		float low = 1.0 - smoothstep(0.06, 0.3, l.y);
		float lobe = 0.15 + 0.85 * leafPhase(c, mix(0.6, 0.35, low));
		// the faces turned away from the light (half on an edge-on face)
		return 0.75 * lobe * through * clamp(0.5 - dot(n, l), 0.0, 1.0);
	}
	float c2 = max(c, 0.0) * max(c, 0.0);
	return 0.32 * (0.06 + 0.94 * c2 * c2) * through;
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
// Workstream C: what is seen of a near crown from afar is its outer shell, sunlit on top and on its sunward side (the
// heart is hidden), so the far leaves keep most of the sun (0.56 made the far woods a dull, flat carpet beside the
// sunlit near crowns); a far crown's own form comes from its faces (leafFacing) and its shade from the far rules in
// program/deferred.glsl.
const float FAR_CANOPY_SUN = 0.85;
const float FAR_CANOPY_FILL = 0.8;
float farCanopySun(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? FAR_CANOPY_SUN : 1.0;
#else
	return 1.0;
#endif
}

float farCanopyFill(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? FAR_CANOPY_FILL : 1.0;
#else
	return 1.0;
#endif
}
#endif
