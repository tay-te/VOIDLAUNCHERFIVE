// VOID Welkin: cloud shadows, read in deferred. From the lit surface the sun's ray goes up to each deck's middle
// (two plane intersections, no march) and reads the cloud map there (one texel), at the same ring the march would
// draw that cell at, so the shadows are the visible clouds' own cells. The surface point is first snapped to
// the world's 16-pixel grid, so a shadow's edge steps with the block texels instead of cutting them.
//
// Returns the sun's share in flat steps: 1 in the open, 0.62 under one deck (about half as bright as the field
// beside it, as Gloam's shadow picture was), 0.52 under two, 0.45 under three or a storm's anvil over a tower. The
// shadows fade in smoothly as the sun climbs past about 5 degrees (no pop, and no long streaks at a low sun).
// Multiply only the direct sun term by it (lib/light.glsl's `direct`), never the sky light.
//
// Cost: CLOUD_SHADOWS texel fetches of the cloud map and a little arithmetic, only for pixels the sun reaches (the
// caller checks sky light and the facing first).

#ifndef WELKIN_CLOUD_SHADOW
#define WELKIN_CLOUD_SHADOW

#include "/lib/clouds/map.glsl"

// `eyePos`: the surface minus cameraPosition (deferred's eyePos).
float welkinCloudShadow(vec3 eyePos, CloudEnv e) {
#if CLOUD_SHADOWS == 0 || !defined CLOUDS
	return 1.0;
#else
	vec3 l = e.sun;
	float rise = smoothstep(0.08, 0.16, l.y);
	if (rise <= 0.0) {
		return 1.0; // the sun too low: no shadows rather than long streaks
	}
	vec3 snapped = (floor((cameraPositionFract + eyePos) * 16.0) + 0.5) / 16.0 - cameraPositionFract;
	float y = e.camY + snapped.y;
	int under = 0;
	for (int k = 0; k < CLOUD_SHADOWS; k++) {
		// the main deck first, then the low stratus, then the high deck
		float deck = k == 0 ? e.layers.y : (k == 1 ? e.layers.x : e.layers.z);
		if (!wcPresent(deck)) {
			continue;
		}
		float mid = deck + WC_DECK * 0.5;
		if (mid <= y) {
			continue;
		}
		vec2 q = snapped.xz + l.xz * ((mid - y) / l.y);
		int lv = wcLevel(length(q));
		CloudCol c = wcMapColumn(wcCellOf(q, lv, e), lv, e);
		if (wcInside(c, mid)) {
			under++;
		}
	}
	return mix(1.0, under == 0 ? 1.0 : (under == 1 ? 0.62 : (under == 2 ? 0.52 : 0.45)), rise);
#endif
}

#endif
