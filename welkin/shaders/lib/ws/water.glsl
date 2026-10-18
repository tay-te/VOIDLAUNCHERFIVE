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
//
// Merged onto the lighting (2026-10-16): the body is lit in the light pass's linear light and tone (its albedo goes
// into welkinLightCore, not multiplied onto a white face's toned light afterwards), with the sky's irradiance as its
// fill, the sun through the shadow map and the clouds' shadows; its colour is the biome tint with the cobalt taken out
// (lib/watersurf.glsl waterTone, as the lighting's water had it); every top, sloped ones too, carries the waves and
// takes its light as level water, and a fall's upright sheet reflects about a normal leaning well up
// (welkinWaterNormal, waterReflectNormal), so a fall's facets show no light and dark triangles.

#ifndef WELKIN_WS_WATER
#define WELKIN_WS_WATER

#include "/lib/watersurf.glsl"

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

// ---- falling water (the defects pass, 2026-10-16)
// A fall's sheet and a cascade's steep steps: translucent white-blue water with streaks of foam pouring down it in the
// block's own pixels (each 1/16-block column its own speed, the streaks stepping a pixel at a time, as vanilla's
// textures move), reflecting the sky about one normal that faces the eye for every face of the fall, so its sides and
// its sloped steps read as one sheet (they took their Fresnel each about its own face before: the steps seen edge-on
// mirrored the bright sky, pale triangles on a darker sheet).

uint wsHashU(uint x) {
	x ^= x >> 16;
	x *= 0x7feb352du;
	x ^= x >> 15;
	x *= 0x846ca68bu;
	x ^= x >> 16;
	return x;
}

float wsHash1(int a, int b) {
	return float(wsHashU(uint(a) * 0x9E3779B1u + uint(b) * 0x85EBCA77u + 0x5EEDu) >> 8) / 16777216.0;
}

// Smooth 1D value noise along x for column `c` (lattice of whole units).
float wsNoise1(float x, int c) {
	float i = floor(x);
	float f = x - i;
	f = f * f * (3.0 - 2.0 * f);
	return mix(wsHash1(int(i), c), wsHash1(int(i) + 1, c), f);
}

// Smooth 2D value noise on a lattice of whole units.
float wsNoise2(vec2 p, int salt) {
	vec2 i = floor(p);
	vec2 f = p - i;
	f = f * f * (3.0 - 2.0 * f);
	ivec2 c = ivec2(i);
	float a = wsHash1(c.x + salt * 7919, c.y), b = wsHash1(c.x + 1 + salt * 7919, c.y);
	float d = wsHash1(c.x + salt * 7919, c.y + 1), e = wsHash1(c.x + 1 + salt * 7919, c.y + 1);
	return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

// The world position wrapped to 1024 blocks (exact anywhere), for patterns on the block grid.
vec3 wsWorldWrapped(vec3 playerPos) {
	return vec3(cameraPositionInt & 1023) + cameraPositionFract + playerPos;
}

// The foam pouring down a fall's face (0..1), in 1/16-block pixels. n: the face's world normal (its horizontal part
// sets which way runs across the fall), t: the clock.
float wsFallFoam(vec3 wp, vec3 n, float t) {
	vec2 hz = n.xz / max(length(n.xz), 1e-4);
	float colF = floor(dot(wp.xz, vec2(-hz.y, hz.x)) * 16.0);
	// (water2, 2026-10-18) the foam pours down the face (on a fall's tilted steps, down their slope): along it, the
	// height less the distance downhill
	float along = wp.y - dot(wp.xz, hz);
	// ropes a few pixels wide, each band of columns at its own speed, 6 to 8 blocks a second, stepping a pixel at a
	// time; neighbouring columns move together (each column on its own drew a barcode of scratches: ice)
	float speed = 6.0 + 2.0 * wsNoise1(colF * 0.15, 31);
	float v = floor((along + t * speed) * 16.0) / 16.0;
	float s = 0.65 * wsNoise2(vec2(colF * 0.2, v * 0.9), 21) + 0.35 * wsNoise2(vec2(colF * 0.5, v * 2.4), 23);
	return smoothstep(0.50, 0.78, s);
}

// Foam on water that is still running (a fall's foot, eddies below a cascade): drifting patches in 1/16-block pixels,
// carried down the slope `down` (horizontal, unit or zero).
float wsRunFoam(vec3 wp, vec2 down, float t) {
	vec2 p = (floor(wp.xz * 16.0) + 0.5) / 16.0 - down * t * 1.2;
	float a = wsNoise2(p * 0.9, 3);
	float b = wsNoise2(p * 2.3 + 17.0, 5);
	return smoothstep(0.55, 0.85, 0.6 * a + 0.4 * b);
}

vec4 wsShadeWater(WsWater w, WsIn inp, WsSeason sea, SkyFrame sf, out vec4 info) {
	float dist = length(w.playerPos);
	vec3 V = w.playerPos / max(dist, 1e-4); // camera to surface
	vec3 L = lightDirWorld();
	bool top = w.geoNormal.y > 0.1; // (sloped tops too: a river's flowing surface ripples as a lake's does)
	bool fromBelow = dot(V, w.geoNormal) > 0.0; // the eye is under the surface
	float kind = w.far ? WS_KIND_FAR_WATER : WS_KIND_WATER;

	// the surface: ripples on the top faces (falls and flowing sides keep their face)
	float shelter = smoothstep(0.4, 0.9, w.lm.y); // wind only roughens open water
	float var = 0.0;
	float energy = 0.0;
	vec3 N = waterReflectNormal(w.geoNormal);
	if (top) {
		N = wsWaterNormal(w.playerPos.xz, w.footprint, inp.wind, inp.rain, shelter, frameTimeCounter, var, energy);
	}
	// (the defects pass) water on the move: a fall's sheet and a cascade's steep steps (fall 1), and below it the
	// gently sloped water still running off (run, a river's own slope is under it). One value per face: the faces'
	// normals are per quad, so neither splits a quad into triangles.
	float fall = fromBelow ? 0.0 : 1.0 - smoothstep(0.93, 0.985, w.geoNormal.y);
	float run = top && !fromBelow ? (1.0 - smoothstep(0.985, 0.9993, w.geoNormal.y)) * (1.0 - fall) : 0.0;
	vec3 wp = wsWorldWrapped(w.playerPos);
	// (water2: the floor of foam 0.06, was 0.18, which laid a pale veil over the whole sheet: ice; the ropes stand out
	// white on a clear sheet)
	float foam = fall > 0.0 ? max(wsFallFoam(wp, w.geoNormal, frameTimeCounter), WS_FALL_FOAM_FLOOR) * fall : 0.0;
	if (run > 0.0) {
		vec2 down = w.geoNormal.xz / max(length(w.geoNormal.xz), 1e-4);
		foam = max(foam, wsRunFoam(wp, down, frameTimeCounter) * run * 0.85);
	}
	// a fall reflects about one normal facing the eye and leaning up, the same for all its faces
	vec3 toEye = vec3(-V.x, 0.0, -V.z);
	toEye = dot(toEye, toEye) > 1e-6 ? normalize(toEye) : vec3(0.0, 0.0, 1.0);
	N = normalize(mix(N, normalize(toEye + vec3(0.0, 1.3, 0.0)), fall));

	// the body: the biome tint (the cobalt taken out) over a calmed vanilla texture, lit in the light pass's linear
	// light and tone as level water; the waves' tilt toward the light shows as gentle light and shade
	// (a fall right at the eye: see the spray below; its texture's streaks would be bands a tenth of the screen wide)
	float spray = fall * (1.0 - smoothstep(1.5, 5.0, dist));
	// (water2, 2026-10-18) a fall keeps little of vanilla's flowing texture: on its tilted steps those diagonal grey
	// streaks read as scratched ice; the foam ropes below carry its motion instead
	vec3 albedo = waterTone(w.tint) * mix(vec3(0.75), w.tex, WS_TEXTURE_SHARE * (1.0 - spray) * (1.0 - 0.75 * fall))
		* wsSeasonWater(sea);
	vec3 faceN = welkinWaterNormal(w.geoNormal);
	// (review round 1: a fall's faces take the sun mostly about its one shared normal, so the steps of a cascade do not
	// light up one by one into bright edges and dark facets of glass)
	float facing = sunFacing(mix(dot(top ? N : faceN, L), mix(dot(faceN, L), dot(N, L), 0.7), fall));
	vec3 sunShare = w.sunShare * wsSeasonSun(sea);
	// (review round 1) clear water scatters little of a low sun back: most of it glances off the surface or goes down
	// to the bed. Lit as a diffuse face, the open water under a dawn sun behind the eye took the bank's long shadows as
	// dark bars across the lake; a fall's sheet and its foam keep the full light, and the glitter its own
	float bodySun = mix(0.3, 1.0, smoothstep(0.02, 0.35, L.y));

	// opacity from the water the view crosses; rain stirs it up
	float a = w.far ? WS_ALPHA_FAR : WS_ALPHA_MAX;
	if (w.behind > 0.0) {
		float thick = max(w.behind - dist, 0.0);
		float k = WS_ABSORB * (1.0 + 1.5 * inp.rain * shelter);
		a = mix(WS_ALPHA_MIN, WS_ALPHA_MAX, 1.0 - exp(-thick * k));
		if (top && !fromBelow) {
			// (the defects pass, 2026-10-16) foam where the water laps the shore, in the block's pixels: a band that
			// breathes in and out with a slow swell, wider and whiter in the wind (it was a pale band that only
			// milked the shallows over)
			float depthV = thick * abs(V.y);
			float swell = 0.5 + 0.5 * sin(frameTimeCounter * 0.9 + wsNoise2(wp.xz * 0.11, 9) * 6.2832);
			float reachV = mix(0.10, 0.34, swell) * mix(0.7, 1.4, clamp(energy, 0.0, 1.0));
			// (by the water's depth, and by the path through it at a steep bank, where the depth falls off at once)
			float edge = max(1.0 - smoothstep(0.0, reachV, depthV), 1.0 - smoothstep(0.0, reachV * 3.0, thick));
			vec2 fp = (floor(wp.xz * 16.0) + 0.5) / 16.0;
			float pix = smoothstep(0.42, 0.72, 0.65 * wsNoise2(fp * 1.7 + frameTimeCounter * 0.07, 11)
				+ 0.35 * wsNoise2(fp * 4.3 - frameTimeCounter * 0.05, 13));
			pix = mix(pix, 0.5, smoothstep(0.04, 0.12, w.footprint));
			float shoreFoam = edge * mix(0.25, 1.0, pix) * WS_SHORE_FOAM * (1.0 - fall) * mix(0.5, 1.0, shelter);
			foam = max(foam, shoreFoam);
		}
	}
	// a fall: a clear sheet, a little lighter than still water (air in it), the foam white and nearly opaque; foam
	// lies on running water below it too
	// (water2: the sheet keeps the water's own green-blue, a little lighter (0.25 toward the pale, was 0.45: a cyan-white
	// sheet read as ice) and stays clear, the rock showing through; the foam is what is white and opaque)
	albedo = mix(albedo, mix(albedo, vec3(0.70, 0.80, 0.86), 0.25), fall);
	a = mix(a, max(a, WS_ALPHA_FALL), fall);
	// a fall right at the eye (looking through it from behind, or standing in its spray): its pixels would be blocks
	// wider than the screen's tenth, so the sheet goes over to a soft, bright veil of spray, the world dim behind it
	// (water2, 2026-10-18: the veil keeps the ropes, a pixel of foam being no bigger than a texel of the rock beside it
	// at that range, and stays half clear; a flat pale slab of 0.8 hid the view beside a fall)
	foam = mix(foam, 0.15 + 0.6 * foam, spray);
	a = mix(a, 0.55, spray);
	albedo = mix(albedo, vec3(0.86, 0.91, 0.93), foam);
	a = mix(a, 0.95, foam);
	vec3 body = welkinLightCore(albedo, faceN, w.lm, 0.0, sunShare * mix(bodySun, 1.0, max(fall, foam)), wsSeasonAmbient(sea),
		w.reach, facing);
	// (the defects pass) clear water mostly takes light away rather than throwing it back: the body's own light is a
	// share of the lit tint, least in the shallows (they showed the bed through a milky veil at 08:00 and noon), and
	// whole again on a fall and its foam
	body *= mix(mix(WS_SCATTER_SHALLOW, WS_SCATTER_DEEP, smoothstep(WS_ALPHA_MIN, WS_ALPHA_MAX, a)), 1.0, max(fall, foam));
	// (review round 1) a sunlit fall is bright, never blown out: its white foam in the full sun stays under the
	// sunlit pale stone beside it, so its streaks keep their texture
	float bodyY = dot(body, vec3(0.2126, 0.7152, 0.0722));
	body *= mix(1.0, min(1.0, 0.74 / max(bodyY, 1e-3)), fall);

	if (fromBelow) {
		// from under water: the Snell window (about 49 degrees) shows the world above; outside it the surface mirrors
		// the dark water. composite2's water fog then covers it with the rest of the view.
		float cosI = clamp(dot(V, N), 0.0, 1.0);
		float window = smoothstep(0.60, 0.70, cosI);
		float aw = mix(0.90, 0.10, window);
		info = vec4(octEncode(N), 0.0, kind);
		return vec4(mix(body * 0.35, body, window) * aw, aw);
	}

	// the sky in the reflected direction, by Fresnel, dimmed where the sky is hidden
	float F = wsFresnel(dot(-V, N), WS_F0_WATER) * (1.0 - 0.8 * foam);
	// (review round 2) a little more of the sky's mirror at a glance than Schlick's water gives (stylized, as both
	// reference packs): a dawn lake reads the lilac sky, not its own navy body; steep views keep their clear bed
	F = min(F * 1.5, 1.0);
	// (water2) a fall's churned sheet scatters the sky's image rather than mirroring it: a mirror of the bright sky on
	// every step was the glassy, icy look
	F *= 1.0 - 0.55 * fall;
	vec3 R = reflect(V, N);
	R.y = abs(R.y);
	float seen = wsSkySeen(w.lm.y);
	vec3 refl = skyDomeOnly(R, sf) * seen + body * (1.0 - seen) * 0.5;

	// the sun's or the moon's image, where its light reaches the water
	vec3 glint = vec3(0.0);
	if (top) {
		float g = wsGlint(R, L, var) * WS_GLINT_GAIN * mix(0.35, 1.0, F);
		// (the defects pass) the glitter path: the light thrown back by the waves too small to draw, a wide lobe about
		// the sun's image (wider in the wind and with distance), broken near the eye into sparkles on the water's own
		// 1/8-block grid, each redrawn a few times a second at its own moment; far off the sparkles average out smooth
		// (water2, 2026-10-18) the lobe is laid in the waves' slopes, not round the sun's image: the facet that throws
		// the sun to the eye leans by the half-way vector's tilt, and at a low sun the facets leaning toward or away
		// from it reach far down the view while the ones leaning sideways barely move it, so the path runs long and
		// narrow from the horizon to the eye, as on real water, instead of a round spot under the sun
		vec3 Hh = normalize(L - V);
		// (measured from the drawn ripple's own normal, so the path breaks up along the wave crests)
		float cH = max(dot(Hh, N), 1e-2);
		float slope2 = (1.0 - cH * cH) / (cH * cH);
		float wide = WS_GLITTER_SLOPE * mix(0.5, 1.6, clamp(energy, 0.0, 1.3)) + var;
		float lobe = exp(-slope2 / wide) * smoothstep(-0.02, 0.06, L.y);
		vec2 gc = floor(wp.xz * 16.0);
		ivec2 gi = ivec2(gc);
		float slot = floor(frameTimeCounter * 4.0 + wsHash1(gi.x, gi.y) * 8.0);
		float h = wsHash1(gi.x * 31 + int(slot), gi.y * 17 - int(slot));
		float share = WS_GLITTER_SHARE * lobe * lobe;
		float spark = smoothstep(1.0 - share, 1.0 - share + 0.01, h) * lobe;
		// sparkles where a cell is 1.5 to 6 screen pixels: nearer they would be blocks of white, farther they shimmer
		float cellPx = 0.0625 / max(w.footprint, 1e-5);
		float sparkle = smoothstep(1.2, 2.0, cellPx) * (1.0 - smoothstep(8.0, 14.0, cellPx));
		float mean = share * lobe;
		g += (mix(mean, spark, sparkle) * WS_GLITTER_GAIN + lobe * WS_GLITTER_GLOW) * mix(0.5, 1.0, F);
		// (water2) a low sun's light is dimmed and reddened on its long way through the air, but its path on the water
		// is what an evening lake is about: the glitter keeps more of its strength as the sun goes down
		float lowSun = 1.0 + 2.0 * (1.0 - smoothstep(0.04, 0.35, L.y)) * step(0.0, sunAngle < 0.5 ? 1.0 : -1.0);
		glint = skyDirectLight() * sunShare * (w.reach * g * lowSun) * (1.0 - fall) * (1.0 - foam);
	}

	vec3 rgb = body * a * (1.0 - F) + refl * F + glint;
	float alpha = 1.0 - (1.0 - a) * (1.0 - F);
	// (water2) near water seen from the air carries its whole opacity in the kind (0.75 to 1), so composite1 can tint
	// just the bed's share (lib/water.glsl)
	info = vec4(octEncode(N), F * seen, w.far ? kind : 0.75 + 0.25 * clamp(alpha, 0.0, 1.0));
#if WS_DEBUG_VIEW == 1
	return vec4(N * 0.5 + 0.5, 1.0);
#elif WS_DEBUG_VIEW == 2
	return vec4(vec3(w.reach) * sunShare, 1.0);
#elif WS_DEBUG_VIEW == 6
	// the water's inputs: red its sky light, green the body's opacity, blue the reflection's share (x4)
	return vec4(w.lm.y, a, F * 4.0, 1.0);
#elif WS_DEBUG_VIEW == 7
	// the body's lit colour, and the texture and tint behind it (top half / bottom half of each pixel row pair)
	return vec4((int(gl_FragCoord.y) & 2) == 0 ? body : w.tex * w.tint, 1.0);
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
