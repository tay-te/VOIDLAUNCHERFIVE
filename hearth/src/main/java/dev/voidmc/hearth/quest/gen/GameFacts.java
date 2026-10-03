package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.Settlement;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/**
 * What the generator asks of the game about things it names: do they exist, what are they called, what do they cost.
 * The game's implementation reads the registries and the economy service; tests use {@link #PLAIN}.
 */
public interface GameFacts {
	boolean itemExists(Identifier item);

	boolean entityExists(Identifier entity);

	int maxStackSize(Identifier item);

	String itemName(Identifier item);

	String entityName(Identifier entity);

	String biomeName(Identifier biome);

	String structureName(Identifier structure);

	/**
	 * What the settlement's market would pay for {@code count} of the item, in bits, or -1 without an economy (the
	 * generator then uses {@link GoodsItems#baseBits}).
	 */
	long marketPrice(Settlement settlement, Identifier item, int count);

	/** How a coin reward reads: the economy's coin names, or emeralds without the economy. */
	String coinText(long bits);

	/** Every id exists, names are the id's path made readable, no economy. */
	GameFacts PLAIN = new GameFacts() {
		@Override
		public boolean itemExists(Identifier item) {
			return true;
		}

		@Override
		public boolean entityExists(Identifier entity) {
			return true;
		}

		@Override
		public int maxStackSize(Identifier item) {
			return 64;
		}

		@Override
		public String itemName(Identifier item) {
			return humanize(item.getPath());
		}

		@Override
		public String entityName(Identifier entity) {
			return humanize(entity.getPath());
		}

		@Override
		public String biomeName(Identifier biome) {
			return humanize(biome.getPath());
		}

		@Override
		public String structureName(Identifier structure) {
			return humanize(structure.getPath());
		}

		@Override
		public long marketPrice(Settlement settlement, Identifier item, int count) {
			return -1;
		}

		@Override
		public String coinText(long bits) {
			return bits + " bits";
		}
	};

	/** {@code spruce_log} → {@code Spruce Log}. */
	static String humanize(String path) {
		String last = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : last.split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return out.toString();
	}
}
