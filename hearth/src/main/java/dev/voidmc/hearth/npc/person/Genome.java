package dev.voidmc.hearth.npc.person;

import dev.voidmc.hearth.api.Culture;
import net.minecraft.util.RandomSource;

/**
 * How a resident's looks are packed into {@code Resident.appearance}: the int is a small genome, so children can inherit
 * their parents' colouring field by field while every other module just sees a seed.
 *
 * <pre>
 *   bits  0–3   skin tone       (index into the skin palette)
 *   bits  4–7   hair colour
 *   bits  8–10  eye colour
 *   bits 11–14  hair style
 *   bits 15–17  facial hair
 *   bit  18     unusual build   (a woman with broad arms, a man with slim ones)
 *   bits 19–31  detail seed     (freckles, clothes shades, small variations)
 * </pre>
 *
 * Any int is a valid genome: appearances made by another factory (or an old save) still decode to someone plausible.
 */
public final class Genome {
	public static final int SKIN_TONES = 12;
	public static final int HAIR_COLOURS = 12;
	public static final int EYE_COLOURS = 7;

	private Genome() {
	}

	public static int skinTone(int g) {
		return (g & 0xF) % SKIN_TONES;
	}

	public static int hairColour(int g) {
		return (g >>> 4 & 0xF) % HAIR_COLOURS;
	}

	public static int eyeColour(int g) {
		return (g >>> 8 & 0x7) % EYE_COLOURS;
	}

	public static int hairStyle(int g) {
		return g >>> 11 & 0xF;
	}

	public static int facialHair(int g) {
		return g >>> 15 & 0x7;
	}

	public static boolean unusualBuild(int g) {
		return (g >>> 18 & 1) != 0;
	}

	public static int detail(int g) {
		return g >>> 19;
	}

	public static int pack(int skin, int hair, int eyes, int style, int facial, boolean unusualBuild, int detail) {
		return (skin & 0xF) | (hair & 0xF) << 4 | (eyes & 0x7) << 8 | (style & 0xF) << 11 | (facial & 0x7) << 15
			| (unusualBuild ? 1 : 0) << 18 | (detail & 0x1FFF) << 19;
	}

	/** A newcomer's genome, coloured by where their family comes from. */
	public static int random(Culture culture, RandomSource random) {
		CultureLooks looks = CultureLooks.of(culture);
		return pack(looks.skin(random), looks.hair(random), looks.eyes(random), random.nextInt(16), random.nextInt(8),
			random.nextInt(14) == 0, random.nextInt(0x2000));
	}

	/**
	 * A child's genome: each colouring field from one parent or the other (occasionally a fresh one, as families go),
	 * style and detail their own.
	 */
	public static int inherit(int a, int b, Culture culture, RandomSource random) {
		CultureLooks looks = CultureLooks.of(culture);
		int skin = random.nextBoolean() ? skinTone(a) : skinTone(b);
		// Children usually fall between their parents' tones.
		if (Math.abs(skinTone(a) - skinTone(b)) >= 2 && random.nextInt(3) == 0) {
			skin = (skinTone(a) + skinTone(b)) / 2;
		}
		int hair = random.nextInt(12) == 0 ? looks.hair(random) : random.nextBoolean() ? hairColour(a) : hairColour(b);
		int eyes = random.nextInt(10) == 0 ? looks.eyes(random) : random.nextBoolean() ? eyeColour(a) : eyeColour(b);
		return pack(skin, hair, eyes, random.nextInt(16), random.nextInt(8), random.nextInt(14) == 0, random.nextInt(0x2000));
	}

	/** Weights per culture for the colouring of people whose families have lived there long. */
	record CultureLooks(int[] skinWeights, int[] hairWeights, int[] eyeWeights) {
		// Skin tones run light to dark; hair: 0 black, 1 near-black, 2 dark brown, 3 brown, 4 chestnut, 5 auburn,
		// 6 copper, 7 dark blonde, 8 golden, 9 flaxen, 10 ash, 11 strawberry; eyes: dark brown, brown, hazel, amber,
		// green, blue, grey.
		static CultureLooks of(Culture c) {
			return switch (c) {
				case NORTHERN, TUNDRA -> new CultureLooks(
					w(5, 7, 6, 4, 2, 1, 1, 0, 0, 0, 0, 0), w(0, 1, 2, 3, 2, 1, 1, 4, 4, 5, 4, 2), w(0, 1, 2, 0, 3, 7, 5));
				case ALPINE, MOORLAND, MARITIME -> new CultureLooks(
					w(4, 7, 7, 5, 3, 1, 1, 0, 0, 0, 0, 0), w(1, 2, 4, 5, 4, 3, 3, 3, 2, 2, 1, 2), w(1, 3, 3, 1, 4, 5, 4));
				case TEMPERATE -> new CultureLooks(
					w(3, 6, 7, 5, 4, 2, 2, 1, 1, 1, 0, 0), w(2, 2, 4, 5, 4, 2, 2, 3, 2, 1, 1, 1), w(2, 4, 3, 1, 3, 4, 3));
				case WISTERIA, KARST -> new CultureLooks(
					w(1, 3, 6, 7, 6, 4, 2, 1, 0, 0, 0, 0), w(8, 7, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0), w(8, 5, 1, 1, 0, 0, 0));
				case MEDITERRANEAN -> new CultureLooks(
					w(0, 1, 3, 6, 7, 6, 3, 1, 0, 0, 0, 0), w(5, 6, 6, 4, 3, 1, 0, 1, 0, 0, 0, 0), w(5, 5, 4, 2, 2, 1, 1));
				case DESERT, STEPPE, MESA -> new CultureLooks(
					w(0, 0, 1, 3, 5, 7, 7, 5, 3, 1, 0, 0), w(7, 7, 5, 2, 1, 0, 0, 0, 0, 0, 0, 0), w(7, 6, 3, 2, 1, 0, 1));
				case TROPICAL -> new CultureLooks(
					w(0, 0, 0, 1, 3, 5, 7, 7, 5, 3, 2, 1), w(8, 7, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0), w(8, 6, 2, 1, 0, 0, 0));
				case BAYOU -> new CultureLooks(
					w(1, 2, 3, 4, 4, 4, 4, 4, 3, 3, 3, 2), w(4, 4, 4, 4, 3, 2, 2, 2, 1, 1, 0, 1), w(5, 5, 3, 2, 2, 2, 2));
			};
		}

		int skin(RandomSource r) {
			return pick(this.skinWeights, r);
		}

		int hair(RandomSource r) {
			return pick(this.hairWeights, r);
		}

		int eyes(RandomSource r) {
			return pick(this.eyeWeights, r);
		}

		private static int[] w(int... weights) {
			return weights;
		}

		private static int pick(int[] weights, RandomSource r) {
			int total = 0;
			for (int w : weights) {
				total += w;
			}
			int roll = r.nextInt(total);
			for (int i = 0; i < weights.length; i++) {
				roll -= weights[i];
				if (roll < 0) {
					return i;
				}
			}
			return 0;
		}
	}
}
