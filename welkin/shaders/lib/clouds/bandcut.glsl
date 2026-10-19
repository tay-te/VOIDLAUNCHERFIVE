// VOID Welkin (D7): where the march may stop, for the march pass (program/deferred_cloudmarch.glsl) and the clouds pass
// alike: the decks near the eye's height (fog banks there) and each deck's far band cut. Moved out of
// lib/clouds/shade.glsl so the march pass need not include the cloud light.

#ifndef WELKIN_CLOUD_BANDCUT
#define WELKIN_CLOUD_BANDCUT

#include "/lib/clouds/cells.glsl"

// How near the eye is to deck k's slab, 1 inside or at its faces .. 0 from 30 blocks off: a deck at eye height is
// seen edge-on as a hard plane across the land, so there its near cells give way to a fog bank (wcSlabFog).
float wcNearDeck(int k, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	if (!wcPresent(deck)) {
		return 0.0;
	}
	float off = max(max(deck - e.camY, e.camY - deck - wcDeckSpan(k)), 0.0);
	return 1.0 - smoothstep(4.0, 30.0, off);
}

// (D7) Where the march may stop for deck k: where the ray enters its slab if the deck is all band there and not a fog
// bank (wcBandOf's z, times 1 - wcNearDeck, at least 0.999), else 1e30. The same arithmetic as wcBandOf, without the
// weather: the clouds pass works out its cut before the march and the bands themselves after it, so the bands are not
// held through the march's loop.
float wcBandCut(int k, vec3 ro, vec3 rd, float pixAngle, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	float y0 = e.camY + ro.y;
	if (!wcPresent(deck) || abs(rd.y) < 1e-5 || (y0 >= deck && y0 <= deck + wcDeckSpan(k))) {
		return 1e30;
	}
	float tIn = ((rd.y > 0.0 ? deck : deck + wcDeckSpan(k)) - y0) / rd.y;
	float tMid = (deck + 0.5 * WC_DECK - y0) / rd.y;
	if (tIn <= 0.0) {
		return 1e30;
	}
	vec2 q = ro.xz + rd.xz * tMid;
	float cell = float(WC_CELL) * clamp(2.0 * length(q) / WC_RING0, 1.0, float(1 << WC_MAX_LEVEL));
	float rows = cell * abs(rd.y) / max(tMid * pixAngle, 1e-6);
	float weight = k == 2 ? 1.0 - smoothstep(4.0, 12.0, rows) : 1.0 - smoothstep(2.0, 5.0, rows);
	return weight * (1.0 - wcNearDeck(k, e)) >= 0.999 ? tIn : 1e30;
}

#endif
