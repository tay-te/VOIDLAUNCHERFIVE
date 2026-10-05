package dev.voidmc.atlas.core.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.names.NameGen.Feature;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Place names are deterministic, varied, well formed and carry the right word for what they name. */
class NameGenTest {
	private static final long SEED = NameGen.seedHash("survival.example.net", 8_675_309L);
	private static final Pattern SHAPE = Pattern.compile("^(The )?[A-Z][a-z']+( [A-Z][a-z']+)*$");
	private static final Pattern TRIPLED = Pattern.compile("(?i)([a-z])\\1\\1");
	private static final List<String> WATER_ENDINGS = List.of("run", "burn", "brook", "beck", "bourne");

	@Test
	void sameInputsGiveTheSameName() {
		long again = NameGen.seedHash("survival.example.net", 8_675_309L);
		assertEquals(SEED, again);
		for (Feature feature : Feature.values()) {
			for (BiomeFamily family : BiomeFamily.values()) {
				for (long id = -50; id < 50; id++) {
					String first = NameGen.name(SEED, feature, family, id);
					assertEquals(first, NameGen.name(SEED, feature, family, id));
					assertEquals(first, NameGen.name(again, feature, family, id));
				}
			}
		}
		// pinned, so an accidental change to the word lists or the mixing shows up
		// (saved names would change under players' feet), so a change must be deliberate and update these
		assertEquals("Juniperfen Crag", NameGen.name(NameGen.seedHash("pin", 1L), Feature.PEAK, BiomeFamily.PEAKS, 42L));
		assertEquals("Fallowmark Mire", NameGen.name(NameGen.seedHash("pin", 1L), Feature.REGION, BiomeFamily.SWAMP, 7L));
	}

	@Test
	void seedHashMixesBothInputs() {
		assertNotEquals(NameGen.seedHash("a", 1L), NameGen.seedHash("b", 1L));
		assertNotEquals(NameGen.seedHash("a", 1L), NameGen.seedHash("a", 2L));
		assertNotEquals(NameGen.seedHash("", 0L), NameGen.seedHash("ab", 0L));
		assertEquals(NameGen.seedHash(null, 7L), NameGen.seedHash("", 7L));
	}

	@Test
	void differentIdsGiveMostlyDifferentNames() {
		for (Feature feature : List.of(Feature.LAKE, Feature.PEAK, Feature.WATERFALL, Feature.CAVE, Feature.RAVINE)) {
			assertTrue(distinct(feature, BiomeFamily.FOREST, 2000) >= 1900, feature + " distinct names");
		}
		// regions and rivers have deliberately smaller forms ("The Quiet Fells", "Silverrun"), so they repeat more
		assertTrue(distinct(Feature.REGION, BiomeFamily.MOUNTAIN, 2000) >= 1700, "REGION distinct names");
		assertTrue(distinct(Feature.RIVER, BiomeFamily.RIVER, 2000) >= 1600, "RIVER distinct names");
		// and a world's worth of one kind of place almost never repeats
		for (Feature feature : Feature.values()) {
			assertTrue(distinct(feature, BiomeFamily.PLAINS, 200) >= 190, feature + " distinct over 200");
		}
	}

	@Test
	void anotherSeedRenamesTheWorld() {
		long other = NameGen.seedHash("survival.example.net", 8_675_310L);
		for (Feature feature : Feature.values()) {
			int changed = 0;
			for (long id = 0; id < 200; id++) {
				if (!NameGen.name(SEED, feature, BiomeFamily.TAIGA, id).equals(NameGen.name(other, feature, BiomeFamily.TAIGA, id))) {
					changed++;
				}
			}
			assertTrue(changed >= 180, feature + " changed " + changed);
		}
	}

	@Test
	void everyNameIsWellFormed() {
		int count = 0;
		for (Feature feature : Feature.values()) {
			for (BiomeFamily family : BiomeFamily.values()) {
				for (long i = 0; i < 115; i++) {
					long id = i * 0x9E3779B97F4A7C15L + feature.ordinal();
					String name = NameGen.name(SEED, feature, family, id);
					count++;
					assertFalse(name.isEmpty(), "empty");
					assertTrue(name.length() <= NameGen.MAX_LENGTH, "too long: " + name);
					assertTrue(SHAPE.matcher(name).matches(), "shape: " + name);
					assertFalse(TRIPLED.matcher(name).find(), "tripled letter: " + name);
					String[] words = name.toLowerCase(Locale.ROOT).split(" ");
					for (int a = 0; a < words.length; a++) {
						for (int b = 0; b < words.length; b++) {
							if (a != b && !words[b].equals("the")) {
								assertFalse(stem(words[a]).contains(stem(words[b])), "repeated word: " + name);
							}
						}
					}
				}
			}
		}
		assertTrue(count >= 20_000, "checked " + count);
	}

	@Test
	void everyNameCarriesItsFeatureWord() {
		for (BiomeFamily family : BiomeFamily.values()) {
			for (long id = 0; id < 300; id++) {
				String lake = NameGen.name(SEED, Feature.LAKE, family, id);
				assertTrue(lake.startsWith("Lake ") || endsWithWord(lake, "Mere", "Water", "Tarn"), lake);

				String peak = NameGen.name(SEED, Feature.PEAK, family, id);
				assertTrue(peak.startsWith("Mount ") || endsWithWord(peak, "Peak", "Crag", "Tor"), peak);

				String falls = NameGen.name(SEED, Feature.WATERFALL, family, id);
				assertTrue(endsWithWord(falls, "Falls", "Force", "Cascade"), falls);

				String cave = NameGen.name(SEED, Feature.CAVE, family, id);
				assertTrue(endsWithWord(cave, "Cave", "Hollow", "Grotto"), cave);

				String ravine = NameGen.name(SEED, Feature.RAVINE, family, id);
				assertTrue(endsWithWord(ravine, "Gorge", "Ravine", "Chasm"), ravine);

				String river = NameGen.name(SEED, Feature.RIVER, family, id);
				String lower = river.toLowerCase(Locale.ROOT);
				boolean flowing = !river.contains(" ") || river.startsWith("The ");
				assertTrue(river.endsWith(" River") || (flowing && WATER_ENDINGS.stream().anyMatch(lower::endsWith)), river);
			}
		}
		for (long id = 0; id < 300; id++) {
			assertRegionWord(BiomeFamily.MOUNTAIN, id, "Hills", "Heights", "Fells", "Highlands");
			assertRegionWord(BiomeFamily.PEAKS, id, "Range", "Peaks", "Spires", "Crags");
			assertRegionWord(BiomeFamily.SWAMP, id, "Marsh", "Fen", "Mire", "Bog");
			assertRegionWord(BiomeFamily.OCEAN, id, "Sea", "Deep", "Waters");
			assertRegionWord(BiomeFamily.DESERT, id, "Wastes", "Dunes", "Sands", "Expanse");
			assertRegionWord(BiomeFamily.MEADOW, id, "Meadow", "Lea");
			assertRegionWord(BiomeFamily.OTHER, id, "Lands", "Reach", "Vale");
		}
	}

	@Test
	void aboutOneRegionInSixIsTheAdjectiveForm() {
		int the = 0;
		int total = 6000;
		for (long id = 0; id < total; id++) {
			if (NameGen.name(SEED, Feature.REGION, BiomeFamily.FOREST, id).startsWith("The ")) {
				the++;
			}
		}
		assertTrue(the > total / 9 && the < total / 4, "The-form count " + the);
	}

	@Test
	void aMissingBiomeCountsAsOther() {
		for (long id = 0; id < 50; id++) {
			assertEquals(NameGen.name(SEED, Feature.REGION, BiomeFamily.OTHER, id), NameGen.name(SEED, Feature.REGION, null, id));
		}
	}

	/** Not an assertion: ten of each, printed so a person can judge the taste. */
	@Test
	void printSamples() {
		BiomeFamily[] families = BiomeFamily.values();
		long seed = NameGen.seedHash("samples", 2026L);
		for (Feature feature : Feature.values()) {
			StringBuilder line = new StringBuilder(feature.name()).append(": ");
			for (int i = 0; i < 10; i++) {
				BiomeFamily family = families[(i * 7 + feature.ordinal() * 3) % families.length];
				if (i > 0) {
					line.append(" | ");
				}
				line.append(NameGen.name(seed, feature, family, i * 1_000_003L));
				if (feature == Feature.REGION) {
					line.append(" (").append(family.name().toLowerCase(Locale.ROOT)).append(')');
				}
			}
			System.out.println(line);
		}
	}

	private static int distinct(Feature feature, BiomeFamily family, int count) {
		Set<String> names = new HashSet<>();
		for (long id = 0; id < count; id++) {
			names.add(NameGen.name(SEED, feature, family, id * 31 + 7));
		}
		return names.size();
	}

	private static void assertRegionWord(BiomeFamily family, long id, String... words) {
		String name = NameGen.name(SEED, Feature.REGION, family, id);
		assertTrue(endsWithWord(name, words), family + ": " + name);
	}

	private static boolean endsWithWord(String name, String... words) {
		for (String word : words) {
			if (name.endsWith(" " + word)) {
				return true;
			}
		}
		return false;
	}

	private static String stem(String word) {
		if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
			return word.substring(0, word.length() - 1);
		}
		return word;
	}
}
