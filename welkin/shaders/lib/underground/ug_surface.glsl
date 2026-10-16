// VOID Welkin, underground (M5, P6): what the underground does to the rock itself, for deferred. Entry: ugSurface().
//
//   damp walls   in drip columns walls and roofs go darker and cooler, floors a little; on walls, streaks two texels
//                wide hang down from the roof, darker again, with one texel of water running down some of them (the
//                world's 16-pixel grid, so they read as the rock's texture, and fade out where texels get too small)
//   glowworms    in glowworm columns the rock takes a soft teal light that fades smoothly from the roof down over
//                about 10 blocks; roof faces carry tiny specks of glowworm light (2x2 texels, a slow smooth twinkle)
//                and walls a few dim silk threads
//   lava         in lava columns the low rock takes a warm light fading over 8 blocks above the floor
//   ambient      a faint light in the realm's colour, a little brighter in great caverns (UG_AMBIENT)
//
// The column flags are blended between the four U cells round the pixel (UgMix), so none of this switches at a 4 m
// cell's edge. The extra light is face-shaded like the sky's ambient (faceShade), so it keeps the pack's directional
// look; it is a look only: the game's light level (mobs, Hearth's stealth) is untouched.
//
// Four U fetches per pixel (plus the shared state's header texels and the eye's four cells, the same for every pixel).
// Nothing for LOD pixels, or when the camera is not underground; the tile's features fade out by UG_SURFACE_REACH.
//
// Use in deferred, for pixels lit there (colortex2.x > 0.5):
//
//   UgSurface us = ugSurface(ugState(), eyePos, n, dist);
//   albedo *= us.albedo;                                     // before the light
//   lightUgLin = toLinear(us.light);                         // lib/light.glsl: in linear light, inside the light
//   lightEmitLin = toLinear(us.glow);                         //   pass, before the tone (merged onto the lighting)
//   color.rgb = welkinLightGlow(albedo, ...);

#ifndef WELKIN_UG_SURFACE
#define WELKIN_UG_SURFACE

#include "/lib/underground/ug_common.glsl"
#include "/lib/light.glsl"

#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

#define UG_SURFACE_REACH 160.0 // blocks: the tile's features fade out over the last 24
#define UG_GLOW_LIGHT 0.30     // the teal light at the roof, on the display scale of the old light (sky by day ~0.7);
                               // deferred takes it to linear light (toLinear), which matches it in the dark
#define UG_LAVA_LIGHT 0.25

struct UgSurface {
	vec3 albedo; // multiplier on the albedo
	vec3 light;  // extra light: lit colour += albedo * light
	vec3 glow;   // colour the surface gives off: lit colour += glow
};

// bpp: blocks per screen pixel at the surface (blocksPerPixelAt), for how far texel-sized detail shows.
UgSurface ugSurface(UgState s, vec3 eyePos, vec3 n, float dist, float bpp) {
	UgSurface r = UgSurface(vec3(1.0), vec3(0.0), vec3(0.0));
	if (s.mode == UG_MODE_OFF) {
		return r;
	}
	float face = faceShade(n);
	// the faint light of the realm, a little more in open caverns
	r.light = s.fog * UG_AMBIENT * mix(0.6, 1.4, s.open) * s.amount * face;
	if (s.mode != UG_MODE_TILE || dist > UG_SURFACE_REACH) {
		return r;
	}
	UgMix c = ugMix(s, eyePos);
	float fade = s.amount * c.valid * (1.0 - smoothstep(UG_SURFACE_REACH - 24.0, UG_SURFACE_REACH, dist));
	if (fade <= 0.0) {
		return r;
	}
	float below = c.ceilY - eyePos.y; // blocks under the roof
	float above = eyePos.y - c.floorY; // blocks over the floor
	float wall = 1.0 - smoothstep(0.4, 0.6, abs(n.y));
	// texel-sized detail (streaks, specks, threads) only while a texel spans a few pixels: it fades, never pops
	float detail = 1.0 - smoothstep(1.0 / 16.0, 1.0 / 8.0, bpp);
	// the texel this pixel is on, pulled half a texel into the face; its column along the face, and the face's plane
	ivec3 t = worldTexel(eyePos - n * (0.5 / 16.0), 0);
	ivec2 axes = faceAxes(t, n);
	int plane = abs(n.x) > abs(n.z) ? t.x : t.z;

#ifdef UG_DAMP
	float drip = c.flags.y * fade;
	if (drip > 0.0) {
		vec3 mul = n.y > 0.5 ? vec3(0.86, 0.88, 0.90) : vec3(0.72, 0.75, 0.79);
		if (wall * detail > 0.0) {
			ivec3 k = ivec3(axes.x >> 1, plane, 3);
			float h = hash01(k);
			float len = 2.0 + 10.0 * hash01(k + ivec3(0, 0, 17));
			if (h < 0.3 && below < len) {
				mul *= mix(1.0, 0.8, wall * detail);
				// one texel of water running down the streak, near enough to see
				float run = floor(fract(frameTimeCounter * 0.2 + h * 3.3) * len * 16.0) / 16.0;
				float near = 1.0 - smoothstep(16.0, 24.0, dist);
				if (abs(floor(below * 16.0) / 16.0 - run) < 0.5 / 16.0) {
					r.glow += vec3(0.10, 0.13, 0.15) * s.light * near * wall * detail * drip;
				}
			}
		}
		r.albedo = mix(vec3(1.0), mul, drip);
	}
#endif

#ifdef UG_GLOWWORMS
	float glow = c.flags.x * fade;
	if (glow > 0.0) {
		// a soft light from the silk on the roof, fading down the walls over about ten blocks
		r.light += UG_GLOW_COLOR * UG_GLOW_LIGHT * (1.0 - smoothstep(1.0, 10.0, below)) * glow * face;
		if (detail > 0.0) {
			if (n.y < -0.5 || below < 1.5) {
				// specks of glowworm light on the roof: 2x2 texels, each breathing slowly between bright and dim
				ivec3 q = worldTexel(eyePos - n * (0.5 / 8.0), 1);
				float h = hash01(q);
				if (h < 0.06) {
					float on = 0.35 + 0.65 * (0.5 + 0.5 * sin(frameTimeCounter * 0.9 + h * 400.0));
					r.glow += UG_GLOW_SPECK * 0.9 * on * glow * detail;
				}
			} else if (wall > 0.5 && below < 6.0) {
				// silk threads hanging from the roof, one texel wide
				ivec3 k = ivec3(axes.x, plane, 5);
				float h = hash01(k);
				if (h < 0.03 && below < 1.0 + 160.0 * h) {
					r.glow += UG_GLOW_COLOR * 0.22 * glow * detail;
				}
			}
		}
	}
#endif

	// lava's warmth on the low rock
	r.light += UG_EMBER_COLOR * UG_LAVA_LIGHT * (1.0 - smoothstep(1.0, 8.0, above)) * c.water.w * fade * face;
	return r;
}

UgSurface ugSurface(UgState s, vec3 eyePos, vec3 n, float dist) {
	return ugSurface(s, eyePos, n, dist, blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight));
}

#endif
