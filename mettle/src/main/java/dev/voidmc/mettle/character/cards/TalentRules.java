package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Talent;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The talent layer's rules (docs/BUILD_FEEL.md §13), free of players and worlds: slots and the talent draft's cadence by
 * character level, rarity odds and value bands, the hand, the row's slot rules, when the row may be rearranged, and what
 * a row adds. A talent card is a card in the ledger with skill {@link CardData#TALENT}, perk key {@code mettle:talent/<id>}
 * and one roll, the trait {@code mettle:talent/<id>} (its value; 1 for a fixed talent).
 */
public final class TalentRules {
	/** Talent cards' print prefix ("TL-00412"). */
	public static final String PREFIX = "TL";
	/** A talent hand: three talents, keep one. */
	public static final int HAND = 3;
	/** Below this share of health, Grit holds. */
	public static final double GRIT_BELOW = 0.30;
	/** Homebody's reach from the bed or respawn point. */
	public static final double HOMEBODY_BLOCKS = 64;
	/** Light Sleeper wakes you when a hostile comes this near the bed. */
	public static final double LIGHT_SLEEPER_BLOCKS = 10;
	/** "A full night": in bed at least this long (the vanilla sleep counter's 100 ticks) and woken by the morning. */
	public static final long SLEPT_TICKS = 100;
	/** Woken this soon after the day starts counts as woken by the morning. */
	public static final long MORNING_TICKS = 200;

	private TalentRules() {
	}

	/** A talent with its id, for the deal. */
	public record Entry(Identifier id, Talent def) {
	}

	// ---------------------------------------------------------------- keys

	public static ResourceKey<Perk> key(Identifier talent) {
		return ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath(talent.getNamespace(), "talent/" + talent.getPath()));
	}

	/** The trait a talent's value is active as: {@code mettle:talent/fleet}. */
	public static Identifier trait(Identifier talent) {
		return Identifier.fromNamespaceAndPath(talent.getNamespace(), "talent/" + talent.getPath());
	}

	/** {@code mettle:talent/fleet} → {@code mettle:fleet}. */
	public static Identifier id(ResourceKey<Perk> key) {
		String path = key.identifier().getPath();
		return Identifier.fromNamespaceAndPath(key.identifier().getNamespace(), path.substring(path.indexOf('/') + 1));
	}

	// ---------------------------------------------------------------- cadence (§13.1)

	/** Talent slots at a character level: 2 from 3, 3 at 15, 4 at 27, 5 at 39 (config {@code talent_slots}). */
	public static int slots(int characterLevel) {
		int n = 0;
		for (int[] step : CardConfig.get().talent_slots) {
			if (characterLevel >= step[0]) {
				n = Math.max(n, step[1]);
			}
		}
		return n;
	}

	/** Whether reaching this character level deals a talent draft: 3, then every 4 (3, 7, 11 … 47). */
	public static boolean draftAt(int characterLevel) {
		CardConfig c = CardConfig.get();
		return characterLevel >= c.talent_draft_from && (characterLevel - c.talent_draft_from) % c.talent_draft_every == 0;
	}

	/** Talent drafts from character level 1 up to and including {@code characterLevel}. */
	public static int draftsUpTo(int characterLevel) {
		CardConfig c = CardConfig.get();
		return characterLevel < c.talent_draft_from ? 0 : (characterLevel - c.talent_draft_from) / c.talent_draft_every + 1;
	}

	/** Talent drafts earned going from level {@code from} (already paid) to {@code to}. */
	public static int draftsBetween(int from, int to) {
		return to <= from ? 0 : draftsUpTo(to) - draftsUpTo(from);
	}

	// ---------------------------------------------------------------- rolling (§13.1)

	/** The rarity odds at a character level, in percent: Common 70 − 0.6 CL, Rare 24 + 0.4 CL, Epic 6 + 0.2 CL. */
	public static double[] odds(int characterLevel) {
		double[] o = CardConfig.get().talent_rarity_odds;
		double common = Math.max(0, o[0] + o[1] * characterLevel);
		double rare = Math.max(0, o[2] + o[3] * characterLevel);
		double epic = Math.max(0, o[4] + o[5] * characterLevel);
		return new double[] {common, rare, epic};
	}

	/** A rarity from a roll in [0, 1). */
	public static Rarity rarity(int characterLevel, double roll) {
		double[] o = odds(characterLevel);
		double total = o[0] + o[1] + o[2];
		double x = roll * total;
		return x < o[0] ? Rarity.COMMON : x < o[0] + o[1] ? Rarity.RARE : Rarity.EPIC;
	}

	/** A rarity's value band: Common the low two thirds, Rare the whole range, Epic the top two thirds (as traits roll). */
	public static double[] band(Talent t, Rarity rarity) {
		double span = t.max() - t.min();
		double lo = rarity == Rarity.EPIC ? t.min() + span / 3 : t.min();
		double hi = rarity == Rarity.COMMON ? t.min() + span * 2 / 3 : t.max();
		return new double[] {lo, hi};
	}

	/** A value in the band, on the talent's step: every step inside the band equally likely, as a trait rolls (CardRules.rollValue). */
	public static double value(Talent t, Rarity rarity, Random random) {
		if (t.isFixed()) {
			return 1;
		}
		double[] band = band(t, rarity);
		double step = t.step() <= 0 ? 1 : t.step();
		long lo = (long) Math.ceil(band[0] / step - 1e-9);
		long hi = (long) Math.floor(band[1] / step + 1e-9);
		double roll = random.nextDouble();
		long pick = hi < lo ? Math.round((band[0] + band[1]) / 2 / step) : Math.min(hi, lo + (long) (roll * (hi - lo + 1)));
		return round(pick * step);
	}

	static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

	/** The rarity a fixed talent always has. */
	public static Rarity fixedRarity(Talent t) {
		return t.fixed().map(f -> f.equalsIgnoreCase("epic") ? Rarity.EPIC : Rarity.RARE).orElse(Rarity.COMMON);
	}

	/** One talent offer: its rarity by character level (a fixed talent keeps its own), its value rolled in the band. */
	public static DraftState.Card roll(Identifier id, Talent t, int characterLevel, Random random) {
		Rarity rarity = t.isFixed() ? fixedRarity(t) : rarity(characterLevel, random.nextDouble());
		double v = value(t, rarity, random);
		return new DraftState.Card(key(id), 1, rarity, false, List.of(), List.of(new TraitRoll(trait(id), v)), 0);
	}

	/**
	 * A talent hand: three different talents, each dealt at ½ weight per copy owned (never below the floor, as skill cards),
	 * each rolled for rarity and value. Fewer talents than three: all of them.
	 */
	public static List<DraftState.Card> deal(List<Entry> all, Function<Identifier, Integer> owned, int characterLevel, Random random) {
		List<Entry> pool = new ArrayList<>(all);
		pool.sort(java.util.Comparator.comparing(e -> e.id().toString()));
		List<DraftState.Card> hand = new ArrayList<>();
		while (hand.size() < HAND && !pool.isEmpty()) {
			double[] w = new double[pool.size()];
			double total = 0;
			for (int i = 0; i < pool.size(); i++) {
				w[i] = CardRules.duplicateWeight(owned.apply(pool.get(i).id()));
				total += w[i];
			}
			double x = random.nextDouble() * total;
			int pick = pool.size() - 1;
			for (int i = 0; i < pool.size(); i++) {
				x -= w[i];
				if (x < 0) {
					pick = i;
					break;
				}
			}
			Entry e = pool.remove(pick);
			hand.add(roll(e.id(), e.def(), characterLevel, random));
		}
		return hand;
	}

	// ---------------------------------------------------------------- what a row adds

	/** A talent card's value (its one roll). */
	public static double value(CardData card) {
		return card.traits().isEmpty() ? 0 : card.traits().getFirst().value();
	}

	/**
	 * The row's values as traits ({@code mettle:talent/fleet} → 3): slotted talent cards in the slots the character level
	 * gives (any beyond them do nothing), one card per talent (the better copy counts).
	 */
	public static Map<Identifier, Double> values(List<CardData> talents, int characterLevel) {
		int slots = slots(characterLevel);
		Map<Identifier, Double> out = new HashMap<>();
		for (CardData c : talents) {
			if (!c.talent() || !c.slotted() || c.place().index() >= slots || c.traits().isEmpty()) {
				continue;
			}
			TraitRoll r = c.traits().getFirst();
			out.merge(r.trait(), r.value(), Math::max);
		}
		return out;
	}

	/** A talent card's size in CARDS.md §14 points (the simulator's): its value ÷ its max, a fixed one 0.75 Rare / 1 Epic. */
	public static double points(Talent t, Rarity rarity, double value) {
		if (t.isFixed()) {
			return rarity == Rarity.EPIC ? 1.0 : 0.75;
		}
		return t.max() <= 0 ? 0 : value / t.max();
	}

	/** Whether {@code a} is a better copy of the same talent than {@code b}: a higher value, then a higher rarity. */
	public static boolean better(CardData a, CardData b) {
		double va = value(a);
		double vb = value(b);
		if (Math.abs(va - vb) > 1e-9) {
			return va > vb;
		}
		return a.rarity().ordinal() > b.rarity().ordinal();
	}

	// ---------------------------------------------------------------- the row's rules (§13.1)

	/** Where a slotted card would go: the slot, and the worse copy of the same talent it replaces (if any). */
	public record Slotting(int at, Optional<CardData> replaces) {
	}

	/**
	 * Whether a talent card may go in slot {@code index} (-1: the first free) of the row: it is a talent, in the binder, the
	 * row has the slot; one card per talent: a better copy takes a worse copy's slot (free: an upgrade), a worse or equal
	 * one is refused. Putting a card in an empty slot is free any time; taking one out is {@link #rearrange}'s.
	 */
	public static Optional<CardRules.Refusal> canSlot(CardData card, int index, List<CardData> row, int slots) {
		return Optional.ofNullable(refusal(card, index, row, slots));
	}

	/** {@link #canSlot}'s answer as where it goes; empty when refused. */
	public static Optional<Slotting> slotting(CardData card, int index, List<CardData> row, int slots) {
		if (refusal(card, index, row, slots) != null) {
			return Optional.empty();
		}
		Optional<CardData> same = row.stream().filter(c -> c.perk().equals(card.perk()) && c.id() != card.id()).findFirst();
		if (same.isPresent()) {
			return Optional.of(new Slotting(same.get().place().index(), same));
		}
		return Optional.of(new Slotting(index < 0 ? CardRules.firstFree(row, slots) : index, Optional.empty()));
	}

	private static CardRules.Refusal refusal(CardData card, int index, List<CardData> row, int slots) {
		if (!card.talent()) {
			return CardRules.Refusal.NOT_A_TALENT;
		}
		if (card.place().kind() != CardData.PlaceKind.BINDER) {
			return CardRules.Refusal.NOT_IN_BINDER;
		}
		if (slots <= 0) {
			return CardRules.Refusal.TALENT_SLOTS;
		}
		Optional<CardData> same = row.stream().filter(c -> c.perk().equals(card.perk()) && c.id() != card.id()).findFirst();
		if (same.isPresent()) {
			return better(card, same.get()) ? null : CardRules.Refusal.SAME_TALENT;
		}
		int at = index < 0 ? CardRules.firstFree(row, slots) : index;
		if (at < 0 || at >= slots) {
			return CardRules.Refusal.NO_SLOT;
		}
		for (CardData c : row) {
			if (c.place().index() == at) {
				return CardRules.Refusal.SLOT_TAKEN;
			}
		}
		return null;
	}

	/** How the row may be rearranged now (§13.1): free below the low-level line, on waking, or with a free swap. */
	public enum Way {
		LOW_LEVEL, WAKE, FREE_SWAP
	}

	/** The way a card may leave the row now, or empty (refused: wait for a night's sleep or a free swap). */
	public static Optional<Way> rearrange(int characterLevel, boolean awake, int freeSwaps) {
		if (characterLevel < CardConfig.get().free_rebuild_below_level) {
			return Optional.of(Way.LOW_LEVEL);
		}
		if (awake) {
			return Optional.of(Way.WAKE);
		}
		return freeSwaps > 0 ? Optional.of(Way.FREE_SWAP) : Optional.empty();
	}

	/**
	 * The waking window (§13.1): woken by the morning after at least {@link #SLEPT_TICKS} in bed (a full night's sleep), the
	 * row may be rearranged freely until the returned game time; anything else (getting up in the night, a nap) opens
	 * nothing (0).
	 *
	 * @param sleptTicks how long the player lay in bed
	 * @param dayTime    the world's day time when they woke (0 = dawn)
	 */
	public static long wakeUntil(long sleptTicks, long dayTime, long gameTime) {
		int seconds = CardConfig.get().talent_swap_on_wake_s;
		if (seconds <= 0 || sleptTicks < SLEPT_TICKS || Math.floorMod(dayTime, 24000L) >= MORNING_TICKS) {
			return 0;
		}
		return gameTime + seconds * 20L;
	}

	/** Talent ids owned, counted (for the deal's duplicate weight). */
	public static Map<Identifier, Integer> owned(List<CardData> talents) {
		Map<Identifier, Integer> out = new HashMap<>();
		for (CardData c : talents) {
			c.talentId().ifPresent(id -> out.merge(id, 1, Integer::sum));
		}
		return out;
	}

	/** Every talent card's id is distinct within a hand (for tests and the guard on a dealt hand). */
	public static boolean distinct(List<DraftState.Card> hand) {
		Set<ResourceKey<Perk>> seen = new HashSet<>();
		return hand.stream().allMatch(c -> seen.add(c.perk()));
	}
}
