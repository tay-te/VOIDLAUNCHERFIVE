// VOID Welkin: debug views for composite (DEBUG_VIEW in lib/settings.glsl).
//
//   1 depth    chunks warm, LOD cool, each banded every doubling of distance (eight shades per octave), so the bands
//              must run on unbroken across the join if both depths are read right. Magenta: the LOD's surface stands
//              nearer than the chunks' (overlap at the join). Red: an LOD depth outside dhNearPlane..dhFarPlane, which
//              means the depth convention is read wrong. Sky black.
//   2 normals  world normal as colour (deferred-lit pixels; others grey)
//   3 light    light map levels: block red, sky green
//   4 albedo   colour before lighting (deferred skips its lighting in this view)
//   5 flags    colortex2: red lit in deferred, green material id x 16, blue emission
//   6 bridge   VOID bridge status in the top-left square: red none, yellow data texture only, green full; with
//              data, the N0 tile drawn 1:1 next to it (blue: lowness)
//   7 tiles    the VOID near tiles (N0, N1) projected onto the land, with their centre and the mist's top
//              (lib/mist.glsl, mistDebugTiles)
//   8 classes  material class per pixel (welkinMatDebugColour); pixels lit in their own program drawn dark
//   9 lights   light colour class per pixel, brighter where it glows; near black for no light class

#ifndef WELKIN_DEBUG
#define WELKIN_DEBUG

#include "/lib/common.glsl"
#include "/lib/depth.glsl"
#include "/lib/void.glsl"
#include "/lib/mist.glsl"

#ifndef WELKIN_GBUFFER_SAMPLERS
#define WELKIN_GBUFFER_SAMPLERS
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhNearPlane
#define WU_dhNearPlane
uniform float dhNearPlane;
#endif
#ifndef WU_dhFarPlane
#define WU_dhFarPlane
uniform float dhFarPlane;
#endif
#endif

vec3 debugView(ivec2 p, vec2 uv, vec3 scene) {
#if DEBUG_VIEW == 1
	Surface s = surfaceAt(p, uv);
	if (s.sky) {
		return vec3(0.0);
	}
	float dist = length(s.view);
	float band = floor(fract(log2(max(dist, 1e-3))) * 8.0) / 8.0;
	float shade = mix(0.3, 1.0, band);
	vec3 c = s.fromLod ? vec3(0.25, 0.55, 1.0) * shade : vec3(1.0, 0.55, 0.2) * shade;
#ifdef DISTANT_HORIZONS
	if (s.lodNearer) {
		c = vec3(1.0, 0.0, 1.0);
	}
	if (s.fromLod && (-s.view.z < dhNearPlane * 0.98 || -s.view.z > dhFarPlane * 1.02)) {
		c = vec3(1.0, 0.0, 0.0);
	}
#endif
	return c;
#elif DEBUG_VIEW == 2
	vec4 f = texelFetch(colortex2, p, 0);
	return f.x > 0.5 ? octDecode(texelFetch(colortex1, p, 0).xy) * 0.5 + 0.5 : vec3(0.5);
#elif DEBUG_VIEW == 3
	vec4 g = texelFetch(colortex1, p, 0);
	return vec3(g.z, g.w, 0.0);
#elif DEBUG_VIEW == 4
	return scene;
#elif DEBUG_VIEW == 5
	vec4 f = texelFetch(colortex2, p, 0);
	return vec3(f.x, fract(f.y * 255.0 / 16.0), f.z);
#elif DEBUG_VIEW == 6
	int mode = voidMode();
	vec3 status = mode == VOID_MODE_FULL ? vec3(0.1, 0.9, 0.2) : (mode == VOID_MODE_HEADER ? vec3(1.0, 0.85, 0.1) : vec3(0.95, 0.1, 0.1));
	// from the top-left corner (gl_FragCoord counts from the bottom)
	ivec2 q = ivec2(p.x, int(viewHeight) - 1 - p.y);
	if (q.x < 48 && q.y < 48) {
		return status;
	}
	if (mode != VOID_MODE_NONE && q.x >= 56 && q.x < 56 + 256 && q.y < 256) {
		vec4 t = texelFetch(void_data, voidTexel(VOID_TILE_N0, q - ivec2(56, 0)), 0);
		return vec3(t.r, t.g, t.b);
	}
	return scene;
#elif DEBUG_VIEW == 7
	return mistDebugTiles(p, uv, scene);
#elif DEBUG_VIEW == 8
	vec4 f = texelFetch(colortex2, p, 0);
	return f.x > 0.5 ? welkinMatDebugColour(int(f.y * 255.0 + 0.5)) : scene * 0.25;
#elif DEBUG_VIEW == 9
	vec4 f = texelFetch(colortex2, p, 0);
	return f.x > 0.5 ? welkinLightDebugColour(unpackLightClass(f.z), unpackEmission(f.z)) : scene * 0.25;
#else
	return scene;
#endif
}

#endif
