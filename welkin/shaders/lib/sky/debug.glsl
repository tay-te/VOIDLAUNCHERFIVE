// VOID Welkin sky (M2): SKY_DEBUG 1 draws the sky table (colortex5) 8x in the bottom-left corner of the screen, with
// a one-pixel grid between texels, so a screenshot shows every palette step the prepare pass chose. Call from
// composite (or final) as the last thing: color.rgb = skyDebugOverlay(ivec2(gl_FragCoord.xy), color.rgb);
// SKY_DEBUG 2 is drawn by the dome itself (band, ring and moon ring as red, green and blue).

#ifndef WELKIN_SKY_DEBUG
#define WELKIN_SKY_DEBUG

#include "/lib/sky/settings.glsl"
#include "/lib/sky/table.glsl"

vec3 skyDebugOverlay(ivec2 p, vec3 scene) {
#if SKY_DEBUG == 1
	const int scale = 8;
	if (p.x < SKY_TABLE_W * scale && p.y < SKY_TABLE_H * scale) {
		ivec2 t = p / scale;
		if (p.x % scale == 0 || p.y % scale == 0) {
			return vec3(0.25);
		}
		return skyTable(t.x, t.y).rgb;
	}
#endif
	return scene;
}

#endif
