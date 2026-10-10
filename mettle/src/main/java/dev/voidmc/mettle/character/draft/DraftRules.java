package dev.voidmc.mettle.character.draft;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.PerkRules;
import dev.voidmc.mettle.core.Curves;
import dev.voidmc.mettle.core.DraftState;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;

/**
 * The perk draft (docs/DRAFT.md), free of players and worlds so it can be tested: which cards a skill's deck holds, the
 * odds of each rarity, dealing a hand, checking a card is still good, and what old saves and respecs turn into.
 *
 * <p>Dealing is deterministic: a hand comes from the player's seed and the number of hands dealt before it, and every
 * list it draws from is sorted by id, so the same character always gets the same hand.
 */
public final class DraftRules {
	/** Cards in a full hand. */
	public static final int HAND = 3;
	/** Rerolls a character starts with (and an old save gets on migration). */
	public static final int START_REROLLS = 2;
	/** Rerolls for each character level gained. */
	public static final int REROLLS_PER_CHARACTER_LEVEL = 1;
	/** Rerolls for each feat earned. */
	public static final int REROLLS_PER_FEAT = 1;
	/** Rerolls for each Hearth trainer's lesson. */
	public static final int REROLLS_PER_LESSON = 1;
	/** Rerolls a character can hold; more are lost, so they stay worth thinking about. */
	public static final int MAX_REROLLS = 5;
	/** The chance that one card of a hand is a wildcard from another skill used lately. */
	public static final double WILDCARD_CHANCE = 0.12;
	/**
	 * Soft attribute gate: a card whose tier asks more of the skill's attribute than the character has (TREES.md §1:
	 * tier II 6, III 10, IV 14) is this much as likely as one whose gate is met.
	 */
	public static final double UNDER_GATE_WEIGHT = 0.4;

	/**
	 * The power budget (docs/DRAFT.md §6): every level to 6 deals a draft; from level 7 only even levels do, and an odd level
	 * brings a reroll instead. Thirteen drafts to skill 20 hold about what the old trees' 20 points bought (13 ranks
	 * against 12); a draft on every level would hand over the whole deck by skill 18 (17 ranks, 1.8 times the depth).
	 * {@link Integer#MAX_VALUE} turns it off.
	 */
	public static final int HALF_RATE_FROM = 7;

	private DraftRules() {
	}

	/** Drafts dealt for a skill going from {@code before} to {@code after} (see {@link #HALF_RATE_FROM}). */
	public static int draftsFor(int before, int after) {
		return draftsFor(before, after, HALF_RATE_FROM);
	}

	public static int draftsFor(int before, int after, int halfRateFrom) {
		int n = 0;
		for (int level = before + 1; level <= after; level++) {
			if (level < halfRateFrom || level % 2 == 0) {
				n++;
			}
		}
		return n;
	}

	/** What dealing needs to know about a character. */
	public interface Sheet {
		int level(ResourceKey<Skill> skill);

		/** The skill's governing attribute, as the character has it now. */
		int attribute(ResourceKey<Skill> skill);

		int rank(ResourceKey<Perk> perk);
	}

	/** Every perk definition, sorted by id so dealing never depends on hash order. */
	public static final class Deck {
		private final Map<ResourceKey<Perk>, Perk> perks = new LinkedHashMap<>();

		public Deck(Map<ResourceKey<Perk>, Perk> perks) {
			perks.entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getKey().identifier().toString()))
				.forEach(e -> this.perks.put(e.getKey(), e.getValue()));
		}

		public Optional<Perk> get(ResourceKey<Perk> key) {
			return Optional.ofNullable(this.perks.get(key));
		}

		/** The drafted perks of one skill (its deck), in id order. */
		public List<ResourceKey<Perk>> of(ResourceKey<Skill> skill) {
			List<ResourceKey<Perk>> out = new ArrayList<>();
			this.perks.forEach((k, p) -> {
				if (p.kind().drafted() && p.skill().equals(skill)) {
					out.add(k);
				}
			});
			return out;
		}

		Map<ResourceKey<Perk>, Perk> all() {
			return this.perks;
		}
	}

	// ---------------------------------------------------------------- rarity and odds

	/** A card's rarity follows how deep its perk sits: roots common, tier II rare, tier III and masteries epic. */
	public static Rarity rarity(Perk perk) {
		return switch (perk.kind()) {
			case CURSED -> Rarity.CURSED;
			case MASTERY -> Rarity.EPIC;
			case PATH -> perk.tier() >= 3 ? Rarity.EPIC : Rarity.RARE;
			default -> Rarity.COMMON;
		};
	}

	/**
	 * The odds, in percent, that a slot is dealt from each rarity at a skill level (docs/DRAFT.md §3). Higher levels shift
	 * them upward a little. A rarity with nothing to deal hands its share to the others in proportion.
	 */
	public static Map<Rarity, Double> odds(int level) {
		int l = Math.clamp(level, 0, Curves.SKILL_CAP);
		Map<Rarity, Double> odds = new EnumMap<>(Rarity.class);
		odds.put(Rarity.COMMON, 62 - 1.5 * l);
		odds.put(Rarity.RARE, 26 + 0.8 * l);
		odds.put(Rarity.EPIC, 8 + 0.6 * l);
		odds.put(Rarity.CURSED, 4 + 0.1 * l);
		return odds;
	}

	// ---------------------------------------------------------------- eligibility

	/** One skill's tree as {@link PerkRules} sees it, for this character. */
	public static PerkRules.TreeView tree(ResourceKey<Skill> skill, Sheet sheet, Deck deck) {
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		Map<ResourceKey<Perk>, Perk> inTree = new HashMap<>();
		deck.all().forEach((k, p) -> {
			if (p.skill().equals(skill)) {
				inTree.put(k, p);
				int rank = sheet.rank(k);
				if (rank > 0) {
					held.put(k, rank);
				}
			}
		});
		return new PerkRules.TreeView(sheet.level(skill), sheet.attribute(skill), 0, held, inTree);
	}

	/** Why the next rank of {@code key} can't be dealt now, or empty when it can. */
	public static Optional<PerkRules.Reason> eligible(ResourceKey<Perk> key, Perk perk, Sheet sheet, Deck deck) {
		return PerkRules.eligible(key, perk, tree(perk.skill(), sheet, deck));
	}

	/** The cards a skill's deck could deal now, in id order. */
	public static List<ResourceKey<Perk>> candidates(ResourceKey<Skill> skill, Sheet sheet, Deck deck) {
		PerkRules.TreeView tree = tree(skill, sheet, deck);
		List<ResourceKey<Perk>> out = new ArrayList<>();
		for (ResourceKey<Perk> key : deck.of(skill)) {
			if (PerkRules.eligible(key, deck.get(key).orElseThrow(), tree).isEmpty()) {
				out.add(key);
			}
		}
		return out;
	}

	/** How likely a card is against the others of its rarity: 1, or {@link #UNDER_GATE_WEIGHT} below its attribute gate. */
	public static double attributeWeight(Perk perk, int attribute) {
		return attribute >= Curves.tierAttributeGate(perk.tier()) ? 1.0 : UNDER_GATE_WEIGHT;
	}

	/** A card that is still good: its perk can be dealt and it gives exactly the next rank. */
	public static boolean valid(Card card, Sheet sheet, Deck deck) {
		Optional<Perk> perk = deck.get(card.perk());
		return perk.isPresent() && sheet.rank(card.perk()) + 1 == card.rank() && eligible(card.perk(), perk.get(), sheet, deck).isEmpty();
	}

	/**
	 * Nothing in the skill's deck will ever be dealt again: every card is at its last rank, or is a Mastery while one is
	 * held. A skill with no deck at all (wave 2, not built yet) is not spent: its drafts wait for it.
	 */
	public static boolean exhausted(ResourceKey<Skill> skill, Sheet sheet, Deck deck) {
		List<ResourceKey<Perk>> cards = deck.of(skill);
		if (cards.isEmpty()) {
			return false;
		}
		boolean mastery = cards.stream().anyMatch(k -> deck.get(k).orElseThrow().kind() == Perk.Kind.MASTERY && sheet.rank(k) > 0);
		for (ResourceKey<Perk> k : cards) {
			Perk p = deck.get(k).orElseThrow();
			boolean spent = sheet.rank(k) >= p.ranks() || p.kind() == Perk.Kind.MASTERY && mastery;
			if (!spent) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- dealing

	/** The player's own seed, from their id. */
	public static long seed(UUID player) {
		return mix(player.getMostSignificantBits() ^ Long.rotateLeft(player.getLeastSignificantBits(), 29));
	}

	/** The random source for the {@code dealt}-th hand. */
	public static Random random(long seed, int dealt) {
		return new Random(mix(seed + (dealt + 1L) * 0x9E3779B97F4A7C15L));
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * Deals a hand for {@code skill}: up to three different cards from its deck, one of them (sometimes) a wildcard from a
	 * skill in {@code recent}. Cards in {@code avoid} (the hand a reroll threw back) are dealt only when nothing else is
	 * left. An empty hand means nothing can be dealt yet: the draft waits.
	 */
	public static List<Card> deal(ResourceKey<Skill> skill, Sheet sheet, Deck deck, Random random, List<ResourceKey<Skill>> recent,
		Set<ResourceKey<Perk>> avoid) {
		List<ResourceKey<Perk>> own = candidates(skill, sheet, deck);
		// Every roll is made whatever comes of it, so the sequence never depends on what the deck holds.
		boolean wild = random.nextDouble() < WILDCARD_CHANCE;
		long wildPick = random.nextLong();
		if (own.isEmpty()) {
			return List.of();
		}
		List<Card> hand = new ArrayList<>(HAND);
		Set<ResourceKey<Perk>> taken = new HashSet<>();
		Card wildcard = null;
		if (wild) {
			List<ResourceKey<Skill>> others = new ArrayList<>();
			for (ResourceKey<Skill> s : recent) {
				if (!s.equals(skill) && !candidates(s, sheet, deck).isEmpty()) {
					others.add(s);
				}
			}
			if (!others.isEmpty()) {
				Random r = new Random(wildPick);
				ResourceKey<Skill> from = others.get(r.nextInt(others.size()));
				ResourceKey<Perk> key = draw(candidates(from, sheet, deck), sheet.level(from), sheet.attribute(from), deck, r, taken, avoid);
				if (key != null) {
					wildcard = card(key, sheet, deck, true);
					taken.add(key);
				}
			}
		}
		int slots = wildcard == null ? HAND : HAND - 1;
		for (int i = 0; i < slots; i++) {
			ResourceKey<Perk> key = draw(own, sheet.level(skill), sheet.attribute(skill), deck, random, taken, avoid);
			if (key == null) {
				break;
			}
			hand.add(card(key, sheet, deck, false));
			taken.add(key);
		}
		if (wildcard != null) {
			hand.add(wildcard);
		}
		return hand;
	}

	private static Card card(ResourceKey<Perk> key, Sheet sheet, Deck deck, boolean wildcard) {
		return new Card(key, sheet.rank(key) + 1, rarity(deck.get(key).orElseThrow()), wildcard);
	}

	/** One weighted draw: each rarity's odds split over its cards, times the soft attribute gate. Null when none is left. */
	static ResourceKey<Perk> draw(List<ResourceKey<Perk>> cards, int level, int attribute, Deck deck, Random random,
		Set<ResourceKey<Perk>> taken, Set<ResourceKey<Perk>> avoid) {
		List<ResourceKey<Perk>> pool = cards.stream().filter(k -> !taken.contains(k) && !avoid.contains(k)).toList();
		if (pool.isEmpty()) {
			pool = cards.stream().filter(k -> !taken.contains(k)).toList();
		}
		if (pool.isEmpty()) {
			return null;
		}
		Map<Rarity, Double> odds = odds(level);
		Map<Rarity, Integer> count = new EnumMap<>(Rarity.class);
		for (ResourceKey<Perk> k : pool) {
			count.merge(rarity(deck.get(k).orElseThrow()), 1, Integer::sum);
		}
		double[] weights = new double[pool.size()];
		double total = 0;
		for (int i = 0; i < pool.size(); i++) {
			Perk p = deck.get(pool.get(i)).orElseThrow();
			Rarity r = rarity(p);
			weights[i] = odds.get(r) / count.get(r) * attributeWeight(p, attribute);
			total += weights[i];
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < pool.size(); i++) {
			roll -= weights[i];
			if (roll < 0) {
				return pool.get(i);
			}
		}
		return pool.getLast();
	}

	/**
	 * The chance, in percent, that one slot of a hand dealt now is each rarity, after empty rarities hand their share on
	 * and the soft attribute gate is applied (what the card frame's legend shows).
	 */
	public static Map<Rarity, Double> slotOdds(ResourceKey<Skill> skill, Sheet sheet, Deck deck) {
		List<ResourceKey<Perk>> cards = candidates(skill, sheet, deck);
		Map<Rarity, Double> odds = odds(sheet.level(skill));
		Map<Rarity, Integer> count = new EnumMap<>(Rarity.class);
		for (ResourceKey<Perk> k : cards) {
			count.merge(rarity(deck.get(k).orElseThrow()), 1, Integer::sum);
		}
		Map<Rarity, Double> weight = new EnumMap<>(Rarity.class);
		double total = 0;
		for (ResourceKey<Perk> k : cards) {
			Perk p = deck.get(k).orElseThrow();
			Rarity r = rarity(p);
			double w = odds.get(r) / count.get(r) * attributeWeight(p, sheet.attribute(skill));
			weight.merge(r, w, Double::sum);
			total += w;
		}
		Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
		for (Rarity r : Rarity.values()) {
			out.put(r, total <= 0 ? 0.0 : 100.0 * weight.getOrDefault(r, 0.0) / total);
		}
		return out;
	}

	// ---------------------------------------------------------------- old saves and respecs

	/**
	 * Perk points an old save (bought trees) had left in a skill: its level less the points its bought perks cost. They
	 * become that many drafts.
	 */
	public static int legacyPoints(int level, Map<ResourceKey<Perk>, Integer> held, Deck deck, ResourceKey<Skill> skill) {
		int spent = 0;
		for (Map.Entry<ResourceKey<Perk>, Integer> e : held.entrySet()) {
			Optional<Perk> p = deck.get(e.getKey());
			if (p.isPresent() && p.get().kind().drafted() && p.get().skill().equals(skill)) {
				spent += p.get().cost() * e.getValue();
			}
		}
		return Math.max(0, level - spent);
	}

	/** A save from before drafts: its unspent points per skill become queued drafts, and it gets the starting rerolls. */
	public static DraftState migrate(long seed, Collection<ResourceKey<Skill>> skills, Map<ResourceKey<Skill>, Integer> levels,
		Map<ResourceKey<Perk>, Integer> held, Deck deck) {
		DraftState state = DraftState.fresh(seed, START_REROLLS);
		for (ResourceKey<Skill> skill : skills) {
			int points = legacyPoints(levels.getOrDefault(skill, 0), held, deck, skill);
			if (points > 0) {
				state = state.withQueued(skill, points);
			}
		}
		return state;
	}

	/**
	 * Drafted ranks per skill among {@code removed} (a respec's refunds): each rank was one card, so it comes back as one
	 * draft for its own skill.
	 */
	public static Map<ResourceKey<Skill>, Integer> returned(Map<ResourceKey<Perk>, Integer> removed, Deck deck) {
		Map<ResourceKey<Skill>, Integer> out = new HashMap<>();
		removed.forEach((k, rank) -> deck.get(k).filter(p -> p.kind().drafted()).ifPresent(p -> out.merge(p.skill(), rank, Integer::sum)));
		return out;
	}

	/** Rerolls after gaining {@code more}, within the cap. */
	public static int addRerolls(int rerolls, int more) {
		return Math.clamp((long) rerolls + more, 0, MAX_REROLLS);
	}
}
