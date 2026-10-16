// VOID Welkin: final. A gentle shoulder for anything lit past white, then invisible anti-banding: one 8-bit step of
// triangular noise, fixed per pixel, so the smooth gradients of the sky, the haze and the light leave the 8-bit
// screen without contour lines. At that size the noise cannot be seen as a pattern. No other post effects: no bloom,
// no TAA, no blur, no palette steps.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

#if WD_BLOOM
#include "/lib/depthfx.glsl"
#ifndef WU_colortex9
#define WU_colortex9
uniform sampler2D colortex9;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
// depth prototype BLOOM: how much of the bright light scatters into the halo (the rest stays where it is)
#if WD_ALL
#define WD_BLOOM_STRENGTH 0.26
#else
#define WD_BLOOM_STRENGTH 0.34
#endif
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 shoulder(vec3 c) {
	// linear to 0.8, then a soft roll into 1.0
	vec3 over = max(c - 0.8, 0.0);
	return min(c, 0.8) + 0.2 * (1.0 - exp(-over * 5.0));
}

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec3 c = max(texelFetch(colortex0, p, 0).rgb, 0.0);
#if WD_BLOOM
	{
		// depth prototype BLOOM: the halo (composite4, half resolution, taken up smoothly) laid on in linear light with
		// headroom, energy-conserving: the pixel lends out the bright share it gave the halo (wdBloomSource) and takes
		// back what the halo brings it. Then back under this pixel's headroom, so a dark pixel by the sun takes the
		// glow whole while a pixel already at white is not pushed further. Textures are never filtered: only the
		// light over the threshold spreads.
		vec4 f = texelFetch(colortex2, p, 0);
		float emit = f.x > 0.5 ? unpackEmission(f.z) : 0.0;
		vec3 lin = pow(c, vec3(2.2));
		float head = wdBloomHeadroom(lin) * (1.0 + 4.0 * emit);
		vec3 own = wdBloomSource(c, emit);
		vec3 glow = texture(colortex9, texcoord).rgb;
		vec3 hdr = lin * head + WD_BLOOM_STRENGTH * (glow - own);
		lin = max(hdr / head, 0.0);
		c = pow(lin, vec3(1.0 / 2.2));
	}
#endif
	c = shoulder(c);
	// two uniform values per pixel summed: a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
	c = clamp(c + tri / 255.0, 0.0, 1.0);
	outColor = vec4(c, 1.0);
}

#endif
