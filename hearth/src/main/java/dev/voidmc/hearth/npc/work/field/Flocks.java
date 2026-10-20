package dev.voidmc.hearth.npc.work.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stocked pastures (docs/LIVING_WORLD_2.md §6.2; the open item of ARCHITECTURE.md §14): how many beasts a pasture keeps and
 * which kinds, and the flock's losses. Nothing new is produced by a flock — the sim credits wool, milk and meat from the
 * herders' time as before — so the size is derived from the books: herders × {@code herd_per_herder}, capped by the
 * pasture's ground and its pens, less the beasts lost and not yet made good. Pure but for the saved losses.
 */
public final class Flocks {
	/** Ground a beast needs, in square blocks of pasture. */
	static final int AREA_PER_BEAST = 12;
	/** Beasts one {@code PEN} point keeps. */
	static final int PER_PEN = 8;

	private Flocks() {
	}

	/**
	 * The flock a pasture keeps.
	 *
	 * @param herders       adult herders working it
	 * @param herdPerHerder the config's beasts per herder
	 * @param area          the pasture's ground (square blocks)
	 * @param pens          its {@code PEN} points (0: no pens drawn, the ground alone caps it)
	 * @param losses        beasts lost and not yet made good
	 */
	public static int size(int herders, int herdPerHerder, int area, int pens, int losses) {
		if (herders <= 0 || herdPerHerder <= 0) {
			return 0;
		}
		int wanted = herders * herdPerHerder;
		int ground = Math.max(1, area / AREA_PER_BEAST);
		int cap = pens > 0 ? Math.min(ground, pens * PER_PEN) : ground;
		return Math.max(0, Math.min(wanted, cap) - Math.max(0, losses));
	}

	/** The beasts a pasture keeps by culture: the first is most of the flock, the rest are mixed in. */
	public enum Species {
		SHEEP, COW, GOAT, PIG
	}

	/** The kinds of beast a culture's herders keep, the main kind first. */
	public static List<Species> species(Culture culture) {
		return switch (culture) {
			case ALPINE -> List.of(Species.COW, Species.GOAT);
			case KARST, MEDITERRANEAN -> List.of(Species.GOAT, Species.SHEEP);
			case DESERT, TUNDRA -> List.of(Species.GOAT);
			case MESA, BAYOU -> List.of(Species.COW, Species.PIG);
			case TROPICAL -> List.of(Species.PIG, Species.GOAT);
			case STEPPE, MOORLAND, NORTHERN, MARITIME, WISTERIA -> List.of(Species.SHEEP);
			default -> List.of(Species.SHEEP, Species.COW);
		};
	}

	/** Which kind the {@code i}th beast of a flock is: three in four the main kind, the others in turn. */
	public static Species speciesOf(Culture culture, int i) {
		List<Species> kinds = species(culture);
		if (kinds.size() == 1 || i % 4 != 3) {
			return kinds.get(0);
		}
		return kinds.get(1 + (i / 4) % (kinds.size() - 1));
	}

	/**
	 * A settlement's flock losses ({@code hearth:npc/flocks}): beasts of each pasture plot killed or stolen, made good at
	 * {@code heal_per_day} a sim day.
	 */
	public static final class Book {
		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("losses", Map.of()).forGetter(b -> {
				Map<String, Integer> m = new HashMap<>();
				b.losses.forEach((k, v) -> m.put(Integer.toString(k), v));
				return m;
			}),
			Codec.LONG.optionalFieldOf("healed", Long.MIN_VALUE).forGetter(b -> b.healedDay)
		).apply(i, (losses, healed) -> {
			Book b = new Book();
			losses.forEach((k, v) -> {
				try {
					b.losses.put(Integer.parseInt(k), v);
				} catch (NumberFormatException ignored) {
					// a hand-edited key: dropped
				}
			});
			b.healedDay = healed;
			return b;
		}));

		final Map<Integer, Integer> losses = new HashMap<>();
		long healedDay = Long.MIN_VALUE;

		public int losses(int plot) {
			return this.losses.getOrDefault(plot, 0);
		}

		/** A beast of a pasture's flock was killed or taken. */
		public void lost(int plot) {
			this.losses.merge(plot, 1, Integer::sum);
		}

		/** Makes good {@code perDay} beasts a pasture for every day since the last; true if anything changed. */
		public boolean heal(long day, int perDay) {
			if (this.healedDay == Long.MIN_VALUE || day < this.healedDay) {
				// first seen (or the clock was set back): count from today
				this.healedDay = day;
				return false;
			}
			long days = day - this.healedDay;
			if (days <= 0) {
				return false;
			}
			this.healedDay = day;
			if (this.losses.isEmpty()) {
				return false;
			}
			long heal = days * perDay;
			this.losses.replaceAll((k, v) -> (int) Math.max(0, v - heal));
			this.losses.values().removeIf(v -> v <= 0);
			return true;
		}

		public boolean isEmpty() {
			return this.losses.isEmpty();
		}
	}

	public static final HearthDataKey<Book> KEY = HearthDataKey.register(Hearth.id("npc/flocks"), HearthDataKey.Scope.SETTLEMENT, Book.CODEC, Book::new);

	/** Loads the key (registered on class load, from {@code FieldWork.init}). */
	static void touch() {
	}
}
