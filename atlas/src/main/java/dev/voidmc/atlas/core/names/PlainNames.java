package dev.voidmc.atlas.core.names;

import dev.voidmc.atlas.api.Place;
import java.util.Locale;

/**
 * What Atlas calls a place when no mod names it: the plain name of the land, as the game does. A region is its biome
 * ("Forest", "Deep Ocean", "Dripstone Caves", in the player's language); water is its ocean or river biome, or a
 * "Lake"; a natural landmark is what it is ("Peak", "Waterfall"). A {@code RegionNameProvider} (VOID Expanse, a VOID
 * server) is asked first and its names win.
 */
public final class PlainNames {
	/** The game's own name for a biome in the player's language, or null if it has none. */
	@FunctionalInterface
	public interface Translator {
		String biome(String biomeId);
	}

	/** No translations: the biome's key, title-cased. */
	public static final Translator UNTRANSLATED = id -> null;

	private PlainNames() {
	}

	/** The plain name of a place of a kind ({@link Place#REGION}, {@link Place#LAKE}...) in a biome. */
	public static String name(String kind, String biomeId, Translator translator) {
		BiomeFamily family = biomeId == null ? BiomeFamily.OTHER : BiomeFamily.of(biomeId);
		return switch (kind) {
			case Place.LAKE -> family == BiomeFamily.OCEAN || family == BiomeFamily.RIVER ? biome(biomeId, translator) : "Lake";
			case Place.RIVER -> family == BiomeFamily.RIVER ? biome(biomeId, translator) : "River";
			case Place.PEAK -> "Peak";
			case Place.WATERFALL -> "Waterfall";
			case Place.RAVINE -> "Ravine";
			case Place.CAVE_MOUTH -> "Cave mouth";
			default -> biomeId == null ? "" : biome(biomeId, translator);
		};
	}

	/** A biome's display name: the game's translation if it has one, else its key title-cased ("deep_dark": "Deep Dark"). */
	public static String biome(String biomeId, Translator translator) {
		String translated = translator == null ? null : translator.biome(biomeId);
		if (translated != null && !translated.isBlank()) {
			return translated.strip();
		}
		return titleCase(biomeId.substring(biomeId.indexOf(':') + 1));
	}

	static String titleCase(String path) {
		StringBuilder b = new StringBuilder(path.length());
		for (String word : path.split("[_/]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (b.length() > 0) {
				b.append(' ');
			}
			b.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return b.toString();
	}
}
