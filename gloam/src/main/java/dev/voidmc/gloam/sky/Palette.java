package dev.voidmc.gloam.sky;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The sky's hand-picked colours (docs/STYLE.md: clean, limited palettes, chosen from rather than blended freely), and
 * the steps every effect moves in.
 *
 * <p>Two kinds of entry. The dawn and dusk colours are the band round the low sun itself, used as they are. The
 * tints (overcast, mist, dust, golden haze, blue hour, moonlight) colour what vanilla's timelines have already made
 * bright or dark: a tint is scaled to the brightness of the colour it replaces ({@link #tone}), so an overcast evening
 * is as dark as vanilla's evening but a clean cool grey instead of a muddy one.
 */
public final class Palette {
	// the band round the low sun, from the sun still up to the last of the afterglow
	public static final Vector3fc GOLD = new Vector3f(1.00F, 0.78F, 0.36F);
	public static final Vector3fc ORANGE = new Vector3f(1.00F, 0.55F, 0.22F);
	public static final Vector3fc ROSE = new Vector3f(0.92F, 0.42F, 0.44F);
	public static final Vector3fc VIOLET = new Vector3f(0.54F, 0.38F, 0.60F);
	public static final Vector3fc DUSK_BLUE = new Vector3f(0.28F, 0.34F, 0.64F);

	// the night
	/** A clear moonlit night's light: silver-blue. */
	public static final Vector3fc MOONLIGHT = new Vector3f(0.60F, 0.70F, 1.00F);
	/** Moonlight through cloud: a slate blue, not a grey. */
	public static final Vector3fc CLOUDED_MOONLIGHT = new Vector3f(0.52F, 0.60F, 0.86F);
	/** A clear sky under a full moon: a deep navy rather than vanilla's black. */
	public static final Vector3fc MOONLIT_SKY = new Vector3f(0.020F, 0.035F, 0.080F);

	// tints, scaled to the brightness of what they colour
	/** An overcast sky: a cool blue-grey. */
	public static final Vector3fc OVERCAST_SKY = new Vector3f(0.66F, 0.71F, 0.80F);
	/** The haze under an overcast: a lighter, cooler grey. */
	public static final Vector3fc OVERCAST_FOG = new Vector3f(0.74F, 0.78F, 0.84F);
	/** Clouds under a heavy front: slate. */
	public static final Vector3fc STORM_CLOUD = new Vector3f(0.52F, 0.56F, 0.66F);
	/** Mist and damp air: a pale, faintly blue white. */
	public static final Vector3fc MIST = new Vector3f(0.86F, 0.90F, 0.94F);
	/** Dust: a warm sand. */
	public static final Vector3fc DUST = new Vector3f(0.90F, 0.78F, 0.60F);
	/** The haze over the land in the golden hour. */
	public static final Vector3fc GOLDEN_HAZE = new Vector3f(1.00F, 0.84F, 0.58F);

	private Palette() {
	}

	/** {@code x} (0..1) in {@code steps} flat steps: 0, 1/steps, .. 1, the nearest one. */
	public static float step(float x, int steps) {
		return Math.round(Math.clamp(x, 0.0F, 1.0F) * steps) / (float) steps;
	}

	/** The brightness of a colour, as vanilla's greyscale weighs it. */
	public static float luma(Vector3fc c) {
		return c.x() * 0.3F + c.y() * 0.59F + c.z() * 0.11F;
	}

	/** The palette colour {@code tint} at the brightness of {@code like} (times {@code brightness}), clamped to 0..1. */
	public static Vector3f tone(Vector3fc tint, Vector3fc like, float brightness) {
		Vector3f t = new Vector3f(tint).mul(luma(like) * brightness / Math.max(1e-4F, luma(tint)));
		return t.set(Math.clamp(t.x, 0.0F, 1.0F), Math.clamp(t.y, 0.0F, 1.0F), Math.clamp(t.z, 0.0F, 1.0F));
	}
}
