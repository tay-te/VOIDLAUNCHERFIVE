package dev.voidmc.gloam.bridge;

import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.sky.Haze;
import dev.voidmc.gloam.sky.Twilight;

/**
 * The pure arithmetic behind the uniforms that are not plain copies of the air: the cloud layers' heights, the haze
 * numbers, the sunset's step and how fiery the day's is. Kept here, not in the sky's colour layers, because those go
 * when the pack draws the sky (milestone M7) and these stay. Unit-tested ({@code AirReadoutTest}).
 */
public final class AirReadout {
	private AirReadout() {
	}

	// ------------------------------------------------------------------------------------------------ cloud layers

	/**
	 * {@code void_cloudLayers}: the cloud decks for a world, in world y: {low, main, high, tower top}, -1 for a deck the
	 * world has none of. The same stack Gloam's own clouds were laid out in ({@code CloudGrid.Layers}): a vanilla-height
	 * world keeps vanilla's one deck with towers to the top of the world; a taller one gets a low stratus 16 over vanilla's
	 * height and the main deck near the top; a tall one (y 600 and more) the low, a main deck 45% of the way up and a thin
	 * high deck just under the top, the highest ranges standing between them. {@code lift} >= 0 is the setting that puts
	 * one deck that far over vanilla's instead.
	 */
	public static int[] cloudLayers(float cloudHeight, int maxY, double lift) {
		int vanilla = Math.round(cloudHeight);
		if (lift >= 0) {
			int main = vanilla + (int) Math.round(lift);
			return new int[]{-1, main, -1, main + 600};
		}
		if (maxY - vanilla < 160) {
			return new int[]{-1, vanilla, -1, Math.max(vanilla + 100, maxY - 8)};
		}
		int low = vanilla + 16;
		if (maxY - vanilla < 300) {
			return new int[]{low, maxY - 40, -1, maxY - 8};
		}
		int high = maxY - 20;
		int main = low + Math.round((high - low) * 0.45F);
		return new int[]{low, main, high, high};
	}

	// ------------------------------------------------------------------------------------------------ haze

	/** The haze reach (blocks) for haziness 0 (the clearest air: a light haze over kilometres) and 1 (the murkiest). */
	public static final float CLEAR_REACH = 8192, MURK_REACH = 80;

	/**
	 * {@code void_haze.x}: the natural log of the haze's extinction per block at the camera's height, for haziness
	 * {@code h} 0..1 (on {@link Haze#haziness}'s log scale, after the hour's thinning and the setting): the pack's
	 * transmittance over {@code d} blocks is {@code exp(-exp(void_haze.x) * d)}, 5% at the reach. The reach runs from
	 * {@value #CLEAR_REACH} blocks at 0 to {@value #MURK_REACH} at 1 on the same log scale, so x runs about -7.9 .. -3.3.
	 */
	public static float logExtinction(float h) {
		double reach = MURK_REACH * Math.pow(CLEAR_REACH / MURK_REACH, 1 - Math.clamp(h, 0, 1));
		return (float) Math.log(3.0 / reach);
	}

	/**
	 * {@code void_haze.z}: how thick a curtain the haze and the mist hang round the camera, 0..0.8 (as Gloam's fog start
	 * had it): the mist while the camera stands in it ({@code inside} 0..1), a hazy land's murk.
	 */
	public static float curtain(float mist, float inside, float outdoors, float haziness) {
		float mistFog = inside * outdoors * Noise.smoothstep(0.04F, 0.35F, mist);
		return Math.min(0.8F, mistFog * (0.3F + 0.7F * mist) + Haze.curtain(haziness));
	}

	/**
	 * {@code void_haze.w}: how thick the far land's haze is against a neutral 1, 0.6..2: a humid lowland in summer
	 * hazier, winter and cold air clearer, an overcast greyer, air washed by rain under a clearing sky clearest, the
	 * morning mist thickening the low haze (as {@code FarAir.hazeThickness} gave VOID LOD), scaled by {@code setting}.
	 */
	public static float farThickness(float humid, float warmth, float overcast, float wetness, float cloudCover, float mist, double setting) {
		float h = Math.clamp(humid, 0, 1);
		float thick = 1.0F + 0.4F * h * Math.max(0, warmth) - 0.2F * Math.max(0, -warmth);
		thick *= 1.0F + 0.25F * overcast;
		thick *= 1.0F - 0.3F * wetness * (1.0F - cloudCover);
		thick += 0.5F * mist;
		thick = Math.clamp(thick, 0.6F, 2.0F);
		return Math.clamp(1.0F + (thick - 1.0F) * (float) setting, 0.6F, 2.0F);
	}

	// ------------------------------------------------------------------------------------------------ the sunset

	/** The dusk times the band round the sun steps at (gold, orange, rose, violet, dusk blue), as {@link Twilight} has them. */
	static final float[] SUNSET_FROM = {0, 12000, 12700, 13500, 14000};

	/** {@code void_sky.w}: the step of the band round the sun, 0 gold .. 4 dusk blue, the same backwards in the morning. */
	public static int sunsetStep(long clock) {
		float u = Twilight.duskTime(clock);
		int i = 0;
		while (i + 1 < SUNSET_FROM.length && u >= SUNSET_FROM[i + 1]) {
			i++;
		}
		return i;
	}

	/** {@code void_sky.z}: how fiery the day's sunrise or sunset is ({@link Twilight#vivid}, 0.75..1.5) as 0..1. */
	public static float vivid(float vivid) {
		return Math.clamp((vivid - 0.75F) / 0.75F, 0.0F, 1.0F);
	}
}
