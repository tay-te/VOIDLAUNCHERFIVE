package dev.voidmc.atlas.core;

/**
 * Colour maths in OKLab, a perceptual space where equal steps of L look like equal steps of lightness: the palette's
 * hillshade and depth steps are made there so they read as even, crafted bands. sRGB to and from linear light goes
 * through tables, so a whole region (262k pixels) shades in a few milliseconds.
 *
 * <p>The powers and roots are {@link StrictMath}'s: the same bits on every JVM and CPU, where {@link Math}'s may differ
 * in the last place (x86 and ARM have different intrinsics), so a colour, and a golden image, is the same everywhere.
 */
public final class Oklab {
	private static final float[] TO_LINEAR = new float[256];
	/** Linear light 0..1 in 4096 steps, to an sRGB channel 0..255. */
	private static final int LUT = 4096;
	private static final int[] TO_SRGB = new int[LUT + 1];

	static {
		for (int i = 0; i < 256; i++) {
			double v = i / 255.0;
			TO_LINEAR[i] = (float) (v <= 0.04045 ? v / 12.92 : StrictMath.pow((v + 0.055) / 1.055, 2.4));
		}
		for (int i = 0; i <= LUT; i++) {
			double l = i / (double) LUT;
			double v = l <= 0.0031308 ? l * 12.92 : 1.055 * StrictMath.pow(l, 1 / 2.4) - 0.055;
			TO_SRGB[i] = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
		}
	}

	private Oklab() {
	}

	public static float linear(int channel) {
		return TO_LINEAR[channel & 0xFF];
	}

	public static int srgb(float linear) {
		if (linear <= 0) {
			return 0;
		}
		if (linear >= 1) {
			return 255;
		}
		return TO_SRGB[(int) (linear * LUT + 0.5F)];
	}

	/** 0xRRGGBB to OKLab {L, a, b} (L 0..1). */
	public static float[] fromRgb(int rgb) {
		float[] out = new float[3];
		fromLinear(linear(rgb >> 16), linear(rgb >> 8), linear(rgb), out);
		return out;
	}

	public static void fromLinear(float r, float g, float b, float[] out) {
		float l = 0.4122214708F * r + 0.5363325363F * g + 0.0514459929F * b;
		float m = 0.2119034982F * r + 0.6806995451F * g + 0.1073969566F * b;
		float s = 0.0883024619F * r + 0.2817188376F * g + 0.6299787005F * b;
		float l_ = (float) StrictMath.cbrt(l);
		float m_ = (float) StrictMath.cbrt(m);
		float s_ = (float) StrictMath.cbrt(s);
		out[0] = 0.2104542553F * l_ + 0.7936177850F * m_ - 0.0040720468F * s_;
		out[1] = 1.9779984951F * l_ - 2.4285922050F * m_ + 0.4505937099F * s_;
		out[2] = 0.0259040371F * l_ + 0.7827717662F * m_ - 0.8086757660F * s_;
	}

	/** OKLab to 0xRRGGBB, clamped into gamut. */
	public static int toRgb(float L, float a, float b) {
		float l_ = L + 0.3963377774F * a + 0.2158037573F * b;
		float m_ = L - 0.1055613458F * a - 0.0638541728F * b;
		float s_ = L - 0.0894841775F * a - 1.2914855480F * b;
		float l = l_ * l_ * l_;
		float m = m_ * m_ * m_;
		float s = s_ * s_ * s_;
		float r = 4.0767416621F * l - 3.3077115913F * m + 0.2309699292F * s;
		float g = -1.2684380046F * l + 2.6097574011F * m - 0.3413193965F * s;
		float bl = -0.0041960863F * l - 0.7034186147F * m + 1.7076147010F * s;
		return srgb(r) << 16 | srgb(g) << 8 | srgb(bl);
	}

	public static int toRgb(float[] lab) {
		return toRgb(lab[0], lab[1], lab[2]);
	}

	/** Chroma (colourfulness) of an OKLab colour. */
	public static float chroma(float[] lab) {
		return (float) StrictMath.hypot(lab[1], lab[2]);
	}

	/** Mixes two 0xRRGGBB colours in linear light. */
	public static int mixRgb(int a, int b, float t) {
		float r = linear(a >> 16) + (linear(b >> 16) - linear(a >> 16)) * t;
		float g = linear(a >> 8) + (linear(b >> 8) - linear(a >> 8)) * t;
		float bl = linear(a) + (linear(b) - linear(a)) * t;
		return srgb(r) << 16 | srgb(g) << 8 | srgb(bl);
	}
}
