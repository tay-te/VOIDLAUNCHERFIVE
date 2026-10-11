// VOID Welkin water: forward shading of the water's surface (gbuffers_water near, dh_water far) and of glass and ice.
//
// The look: smooth ripples (lib/ws/ripples.glsl) that move with Gloam's wind; the sky table's dome in the reflected
// direction (lib/sky/dome.glsl skyDomeOnly), by Schlick's Fresnel, dimmed where the sky is hidden; the sun's (or the
// moon's) image as a crisp-edged glint on calm water that spreads into a glitter path with roughness and distance;
// the body lit by the pack's own light ramp (the sun through the near shadow map, so trees shade the water), its
// opacity from the depth of water the view crosses, with a pale band where it thins out over a bank. No steps, no
// dither. Output is premultiplied alpha (blend ONE ONE_MINUS_SRC_ALPHA, shaders.properties), so a reflection or a glint
// can be brighter than the body.
//
// info (colortex4, unblended) is for composite2: rg the water's normal (octEncode), b the sky reflection's weight,
// a the kind (WS_KIND_*). Its SSR hook (lib/ws/ssr.glsl) swaps that share of sky for the terrain it finds.
//
// Needs lib/light.glsl, lib/sky/dome.glsl and the other lib/ws files first.

#ifndef WELKIN_WS_WATER
#define WELKIN_WS_WATER

struct WsWater {
	vec3 playerPos;  // camera-relative position of the surface
	vec3 geoNormal;  // world normal of the face
	vec2 lm;         // light map 0..1 (x block, with the held light; y sky)
	vec3 tint;       // the biome's water colour (vertex colour)
	vec3 tex;        // the texture's colour (vanilla water); vec3(0.75) where there is none (far water)
	float behind;    // distance from the camera to the opaque surface behind, along this ray; < 0 unknown
	float footprint; // blocks per screen pixel here
	bool far;        // drawn by dh_water
	float reach;     // how far the sun reaches here (lib/shadow.glsl welkinSunReach, or sunOpen on far water)
	vec3 sunShare;   // what of the direct light gets through (shadow map's tint, clouds...)
};

// Schlick's Fresnel.
float wsFresnel(float cosT, float f0) {
	float m = 1.0 - clamp(cosT, 0.0, 1.0);
	float m2 = m * m;
	return f0 + (1.0 - f0) * m2 * m2 * m;
}

// How much of the sky a reflection sees from here: little under a roof or deep in a wood, all of it in the open.
float wsSkySeen(float skyLight) {
	return 0.15 + 0.85 * smoothstep(0.45, 1.0, skyLight);
}

// The sun's (or the moon's) image in a surface of normal roughness `var` (slope variance): a disc of the light's
// angular size with a crisp but smooth edge on calm water, spreading and dimming as the waves too small to draw tilt
// it about. 0..1 at the centre of a calm image.
float wsGlint(vec3 R, vec3 L, float var) {
	const float r2 = WS_GLINT_RADIUS * WS_GLINT_RADIUS;
	float spread2 = r2 + 4.0 * var;
	float theta2 = 2.0 * max(1.0 - dot(R, L), 0.0); // the angle between them, squared (small angles)
	float x = theta2 / spread2;
	return exp(-x * x) * (r2 / spread2) * smoothstep(-0.02, 0.05, L.y);
}

vec4 wsShadeWater(WsWater w, WsIn inp, WsSeason sea, SkyFrame sf, out vec4 info) {
	float dist = length(w.playerPos);
	vec3 V = w.playerPos / max(dist, 1e-4); // camera to surface
	vec3 L = lightDirWorld();
	bool top = w.geoNormal.y > 0.9;
	bool fromBelow = dot(V, w.geoNormal) > 0.0; // the eye is under the surface
	float kind = w.far ? WS_KIND_FAR_WATER : WS_KIND_WATER;

	// the surface: ripples on the top faces (falls and flowing sides keep their face)
	float shelter = smoothstep(0.4, 0.9, w.lm.y); // wind only roughens open water
	float var = 0.0;
	float energy = 0.0;
	vec3 N = w.geoNormal;
	if (top) {
		N = wsWaterNormal(w.playerPos.xz, w.footprint, inp.wind, inp.rain, shelter, frameTimeCounter, var, energy);
	}

	// the body: the biome tint over a calmed vanilla texture, lit by the pack's ramp; the waves' tilt toward the light
	// shows as gentle light and shade
	vec3 albedo = w.tint * mix(vec3(0.75), w.tex, WS_TEXTURE_SHARE) * wsSeasonWater(sea);
	vec3 faceN = top ? vec3(0.0, 1.0, 0.0) : w.geoNormal;
	float facing = sunFacing(dot(top ? N : w.geoNormal, L));
	vec3 sunShare = w.sunShare * wsSeasonSun(sea);
	vec3 light = welkinLightCore(vec3(1.0), faceN, w.lm, 0.0, sunShare, wsSeasonAmbient(sea), w.reach, facing);
	vec3 body = albedo * light;

	// opacity from the water the view crosses; rain stirs it up
	float a = w.far ? WS_ALPHA_FAR : WS_ALPHA_MAX;
	if (w.behind > 0.0) {
		float thick = max(w.behind - dist, 0.0);
		float k = WS_ABSORB * (1.0 + 1.5 * inp.rain * shelter);
		a = mix(WS_ALPHA_MIN, WS_ALPHA_MAX, 1.0 - exp(-thick * k));
		if (top && !fromBelow) {
			// a pale band where the water thins out over the bank
			float bank = (1.0 - smoothstep(0.0, WS_BANK_DEPTH, thick * abs(V.y))) * WS_BANK_STRENGTH;
			body = mix(body, vec3(0.80, 0.86, 0.88) * light, bank);
			a = max(a, mix(a, 0.6, bank));
		}
	}

	if (fromBelow) {
		// from under water: the Snell window (about 49 degrees) shows the world above; outside it the surface mirrors
		// the dark water. composite2's water fog then covers it with the rest of the view.
		float cosI = clamp(dot(V, N), 0.0, 1.0);
		float window = smoothstep(0.60, 0.70, cosI);
		float aw = mix(0.90, 0.10, window);
		info = vec4(octEncode(N), 0.0, kind);
		return vec4(mix(albedo * light * 0.35, body, window) * aw, aw);
	}

	// the sky in the reflected direction, by Fresnel, dimmed where the sky is hidden
	float F = wsFresnel(dot(-V, N), WS_F0_WATER);
	vec3 R = reflect(V, N);
	R.y = abs(R.y);
	float seen = wsSkySeen(w.lm.y);
	vec3 refl = skyDomeOnly(R, sf) * seen + body * (1.0 - seen) * 0.5;

	// the sun's or the moon's image, where its light reaches the water
	vec3 glint = vec3(0.0);
	if (top) {
		glint = skyDirectLight() * sunShare * (w.reach * wsGlint(R, L, var) * WS_GLINT_GAIN * mix(0.35, 1.0, F));
	}

	vec3 rgb = body * a * (1.0 - F) + refl * F + glint;
	float alpha = 1.0 - (1.0 - a) * (1.0 - F);
	info = vec4(octEncode(N), F * seen, kind);
#if WS_DEBUG_VIEW == 1
	return vec4(N * 0.5 + 0.5, 1.0);
#elif WS_DEBUG_VIEW == 2
	return vec4(vec3(w.reach) * sunShare, 1.0);
#endif
	return vec4(rgb, alpha);
}

// Glass, stained glass and ice: their lit colour (as the pack lights them) with the sky's reflection by Fresnel; the
// other translucents (slime, honey, portals) are only lit. lit: the lit colour, alpha: the texture's opacity.
// Premultiplied.
vec4 wsShadeGlass(vec3 lit, float alpha, vec3 playerPos, vec3 n, float skyLight, int material, SkyFrame sf,
		out vec4 info) {
	info = vec4(0.5, 0.5, 0.0, WS_KIND_OTHER);
	if (!matIsGlassLike(material) || material == M_GEL) {
		return vec4(lit * alpha, alpha);
	}
	vec3 V = normalize(playerPos);
	if (dot(V, n) > 0.0) {
		n = -n; // panes are seen from both sides
	}
	float F = wsFresnel(dot(-V, n), matIsIce(material) ? WS_F0_ICE : WS_F0_GLASS);
	vec3 R = reflect(V, n);
	R.y = abs(R.y);
	vec3 refl = skyDomeOnly(R, sf) * wsSkySeen(skyLight);
	return vec4(lit * alpha * (1.0 - F) + refl * F, 1.0 - (1.0 - alpha) * (1.0 - F));
}

#endif
