package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.PerkRules;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.Curves;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Cards v2 (docs/CARDS.md), free of players and worlds so it can be tested: rolling a card (perk by depth, rarity, rank,
 * traits, keywords, requirement), dealing a hand of rolled offers, the deck's slots and slot rules, what a deck makes
 * active (perks, dormancy, trait totals, sets), printed ids, and the v1 → v2 migration.
 *
 * <p>Every roll comes from the player's seed and the hand's place in it, and every list it draws from is sorted by id,
 * so the same character always gets the same offers (DRAFT.md §9).
 */
public final class CardRules {
	/** The keyword every cursed card carries natively (§3.4). */
	public static final Identifier HEX = Identifier.fromNamespaceAndPath("mettle", "hex");
	/** The rarities a card can roll; Cursed is the frame of the cursed perks, never rolled. */
	public static final List<Rarity> ROLLED = List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC);

	private CardRules() {
	}

	// ---------------------------------------------------------------- data a roll needs

	/** A trait with its id. */
	public record TraitEntry(Identifier id, Trait trait) {
	}

	/** The card data: trait pools, native keywords, keyword pools, card prefixes. Sorted by id where it matters. */
	public interface Data {
		/** A skill's trait pool, in id order. */
		List<TraitEntry> traits(ResourceKey<Skill> skill);

		Optional<Trait> trait(Identifier id);

		/** The perk's native keyword. */
		Optional<Identifier> nativeKeyword(ResourceKey<Perk> perk);

		/** The skill's second-keyword pool. */
		List<Identifier> pool(ResourceKey<Skill> skill);

		/** The skill's card id prefix ({@code MN}). */
		String prefix(ResourceKey<Skill> skill);
	}

	/** What dealing and deriving need about a character: skill levels, attributes and how many cards of a perk it owns. */
	public interface Sheet {
		int level(ResourceKey<Skill> skill);

		/** The skill's governing attribute, as the character has it now (the soft gate). */
		int attribute(ResourceKey<Skill> skill);

		/** Cards of this perk the character owns, wherever they are. */
		int owned(ResourceKey<Perk> perk);
	}

	// ---------------------------------------------------------------- rarity, rank, requirement

	/** The rolled rarity odds at a skill level, in percent (§3.2): Common 70 − 1.5 L, Rare 24 + L, Epic 6 + 0.5 L. */
	public static Map<Rarity, Double> rarityOdds(int level) {
		int l = Math.clamp(level, 0, Curves.SKILL_CAP);
		double[] o = CardConfig.get().rarity_odds;
		Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
		out.put(Rarity.COMMON, Math.max(0, o[0] + o[1] * l));
		out.put(Rarity.RARE, Math.max(0, o[2] + o[3] * l));
		out.put(Rarity.EPIC, Math.max(0, o[4] + o[5] * l));
		return out;
	}

	/** Rolls a card's rarity: cursed perks are always Cursed; everything else by {@link #rarityOdds}. */
	public static Rarity rollRarity(Perk perk, int level, Random random) {
		double roll = random.nextDouble();
		if (perk.kind() == Perk.Kind.CURSED) {
			return Rarity.CURSED;
		}
		Map<Rarity, Double> odds = rarityOdds(level);
		double total = odds.values().stream().mapToDouble(Double::doubleValue).sum();
		double r = roll * total;
		for (Rarity rarity : ROLLED) {
			r -= odds.get(rarity);
			if (r < 0) {
				return rarity;
			}
		}
		return Rarity.EPIC;
	}

	/** A root's rank is its rarity (Common I, Rare II, Epic III); everything else has one strength. */
	public static int rank(Perk perk, Rarity rarity) {
		if (perk.kind() != Perk.Kind.ROOT) {
			return 1;
		}
		int r = switch (rarity) {
			case RARE -> 2;
			case EPIC -> 3;
			default -> 1;
		};
		return Math.min(r, perk.ranks());
	}

	/** The rarity a v1 root rank migrates to (§12): I Common, II Rare, III Epic. */
	public static Rarity rarityOfRank(int rank) {
		return rank >= 3 ? Rarity.EPIC : rank == 2 ? Rarity.RARE : Rarity.COMMON;
	}

	/** The skill level a card needs to be slotted (§3.3): its tier's gate, a little more the rarer it is. */
	public static int needs(Perk perk, Rarity rarity) {
		int tier = Math.max(1, perk.tier());
		int base = Curves.tierSkillGate(tier);
		if (rarity == Rarity.CURSED || perk.kind() == Perk.Kind.CURSED) {
			return base;
		}
		int step = switch (rarity) {
			case RARE -> tier == 1 ? 2 : 1;
			case EPIC -> tier == 1 ? 5 : tier == 2 ? 3 : tier == 3 ? 3 : 2;
			default -> 0;
		};
		return Math.min(Curves.SKILL_CAP, base + step);
	}

	/** A migrated card needs only its tier's level (§12), so nothing a player had becomes unusable. */
	public static int migratedNeeds(Perk perk) {
		return Curves.tierSkillGate(Math.max(1, perk.tier()));
	}

	// ---------------------------------------------------------------- traits and keywords

	/** How many traits a card of this rarity carries (§3.4). */
	public static int traitCount(Rarity rarity, Random random) {
		double r = random.nextDouble();
		return switch (rarity) {
			case COMMON -> r < 0.5 ? 0 : 1;
			case RARE -> r < 0.6 ? 1 : 2;
			case EPIC -> r < 0.6 ? 2 : 3;
			case CURSED -> r < 0.5 ? 1 : 2;
			case MYTHIC -> 0;
		};
	}

	/** The band a rarity's values roll in (§3.4): the low two thirds, the whole range, or the top two thirds. */
	public static double[] band(Trait trait, Rarity rarity) {
		double span = trait.max() - trait.min();
		return switch (rarity) {
			case COMMON -> new double[] {trait.min(), trait.min() + span * 2 / 3};
			case EPIC -> new double[] {trait.min() + span / 3, trait.max()};
			default -> new double[] {trait.min(), trait.max()};
		};
	}

	/** A value rolled in the rarity's band, on the trait's step grid (so it always stays in the band). */
	public static double rollValue(Trait trait, Rarity rarity, Random random) {
		double[] band = band(trait, rarity);
		double step = trait.step() <= 0 ? 1 : trait.step();
		long lo = (long) Math.ceil(band[0] / step - 1e-9);
		long hi = (long) Math.floor(band[1] / step + 1e-9);
		double roll = random.nextDouble();
		long pick = hi < lo ? Math.round((band[0] + band[1]) / 2 / step) : Math.min(hi, lo + (long) (roll * (hi - lo + 1)));
		return round(pick * step);
	}

	/** The chance of a second keyword (§3.4). */
	public static double secondKeywordChance(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 0.0;
			case RARE -> 0.3;
			case EPIC -> 0.7;
			case CURSED -> 1.0;
			case MYTHIC -> 0.0;
		};
	}

	/**
	 * Rolls one card for a perk: rarity, rank, traits (distinct, from its skill's pool, at most the count its rarity
	 * rolls), keywords (the native one, maybe a second from the pool, never the same) and the requirement. Every roll is
	 * made whatever comes of it, so the sequence never depends on the data.
	 */
	public static Card roll(ResourceKey<Perk> key, Perk perk, int level, Data data, Random random, boolean wildcard) {
		return roll(key, perk, rollRarity(perk, level, random), data, random, wildcard);
	}

	/** {@link #roll} at a given rarity (ops' {@code /mettle cards give}); a cursed perk is always Cursed. */
	public static Card roll(ResourceKey<Perk> key, Perk perk, Rarity chosen, Data data, Random random, boolean wildcard) {
		Rarity rarity = perk.kind() == Perk.Kind.CURSED ? Rarity.CURSED : chosen == Rarity.CURSED ? Rarity.COMMON : chosen;
		int count = traitCount(rarity, random);
		boolean second = random.nextDouble() < secondKeywordChance(rarity);
		long traitSeed = random.nextLong();
		long keywordSeed = random.nextLong();
		List<TraitRoll> traits = rollTraits(data.traits(perk.skill()), rarity, count, new Random(traitSeed));
		List<Identifier> keywords = keywords(key, perk, data, second, new Random(keywordSeed));
		return new Card(key, rank(perk, rarity), rarity, wildcard, keywords, traits, needs(perk, rarity));
	}

	/** {@code count} distinct traits from a pool, each with a value in the rarity's band. */
	public static List<TraitRoll> rollTraits(List<TraitEntry> pool, Rarity rarity, int count, Random random) {
		List<TraitEntry> left = new ArrayList<>(pool);
		List<TraitRoll> out = new ArrayList<>();
		for (int i = 0; i < count && !left.isEmpty(); i++) {
			TraitEntry t = left.remove(random.nextInt(left.size()));
			out.add(new TraitRoll(t.id(), rollValue(t.trait(), rarity, random)));
		}
		return out;
	}

	/** The native keyword (Hex for cursed perks), then a second from the skill's pool when {@code second}. */
	public static List<Identifier> keywords(ResourceKey<Perk> key, Perk perk, Data data, boolean second, Random random) {
		Identifier first = perk.kind() == Perk.Kind.CURSED ? HEX : data.nativeKeyword(key).orElse(HEX);
		List<Identifier> out = new ArrayList<>(2);
		out.add(first);
		if (second) {
			List<Identifier> pool = data.pool(perk.skill()).stream().filter(k -> !k.equals(first)).sorted(Comparator.comparing(Identifier::toString)).toList();
			if (!pool.isEmpty()) {
				out.add(pool.get(random.nextInt(pool.size())));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- dealing (the perk by depth)

	/**
	 * Why a perk can't be dealt now, or empty when it can (§3.1): v1's hard gates with "learned" read as "owned": the
	 * tier's skill level; a tier III path card needs a tier II card of its path owned; a Mastery needs two cards of its
	 * side owned (any two path cards for the middle one). Owning a perk never stops it being dealt again.
	 */
	public static Optional<PerkRules.Reason> dealable(ResourceKey<Perk> key, Perk perk, Sheet sheet, DraftRules.Deck deck) {
		if (!perk.kind().drafted()) {
			return Optional.of(PerkRules.Reason.NOT_BOUGHT);
		}
		if (sheet.level(perk.skill()) < Curves.tierSkillGate(perk.tier())) {
			return Optional.of(PerkRules.Reason.SKILL_TOO_LOW);
		}
		if (perk.kind() == Perk.Kind.PATH && perk.tier() >= 3 && ownedOnPath(perk.skill(), perk.path(), 2, sheet, deck) == 0) {
			return Optional.of(PerkRules.Reason.NEEDS_PATH_PERK);
		}
		if (perk.kind() == Perk.Kind.MASTERY) {
			int count = "any".equals(perk.side())
				? ownedOnPath(perk.skill(), "a", 0, sheet, deck) + ownedOnPath(perk.skill(), "b", 0, sheet, deck)
				: ownedOnPath(perk.skill(), perk.side(), 0, sheet, deck);
			if (count < 2) {
				return Optional.of(PerkRules.Reason.NEEDS_TWO_PATH_PERKS);
			}
		}
		return Optional.empty();
	}

	/** Distinct path perks of a path (and tier, 0 = any) the character owns a card of. */
	private static int ownedOnPath(ResourceKey<Skill> skill, String path, int tier, Sheet sheet, DraftRules.Deck deck) {
		int n = 0;
		for (ResourceKey<Perk> k : deck.of(skill)) {
			Perk p = deck.get(k).orElseThrow();
			if (p.kind() == Perk.Kind.PATH && p.path().equals(path) && (tier == 0 || p.tier() == tier) && sheet.owned(k) > 0) {
				n++;
			}
		}
		return n;
	}

	/** The perks a skill's deck could deal now, in id order. */
	public static List<ResourceKey<Perk>> candidates(ResourceKey<Skill> skill, Sheet sheet, DraftRules.Deck deck) {
		List<ResourceKey<Perk>> out = new ArrayList<>();
		for (ResourceKey<Perk> key : deck.of(skill)) {
			if (dealable(key, deck.get(key).orElseThrow(), sheet, deck).isEmpty()) {
				out.add(key);
			}
		}
		return out;
	}

	/** The duplicate factor: ½ per copy owned, never below ¼ (§3.1). */
	public static double duplicateWeight(int owned) {
		CardConfig c = CardConfig.get();
		return owned <= 0 ? 1.0 : Math.max(c.duplicate_floor, Math.pow(c.duplicate_weight, owned));
	}

	/**
	 * One weighted draw by depth: v1's rarity odds are the depth odds (root, tier II, tier III or Mastery, cursed), split
	 * over the band's perks, times the soft attribute gate and the duplicate factor. Null when none is left.
	 */
	static ResourceKey<Perk> draw(List<ResourceKey<Perk>> cards, int level, int attribute, Sheet sheet, DraftRules.Deck deck, Random random,
		Set<ResourceKey<Perk>> taken, Set<ResourceKey<Perk>> avoid) {
		List<ResourceKey<Perk>> pool = cards.stream().filter(k -> !taken.contains(k) && !avoid.contains(k)).toList();
		if (pool.isEmpty()) {
			pool = cards.stream().filter(k -> !taken.contains(k)).toList();
		}
		if (pool.isEmpty()) {
			return null;
		}
		Map<Rarity, Double> odds = DraftRules.odds(level);
		Map<Rarity, Integer> count = new EnumMap<>(Rarity.class);
		for (ResourceKey<Perk> k : pool) {
			count.merge(DraftRules.rarity(deck.get(k).orElseThrow()), 1, Integer::sum);
		}
		double[] weights = new double[pool.size()];
		double total = 0;
		for (int i = 0; i < pool.size(); i++) {
			Perk p = deck.get(pool.get(i)).orElseThrow();
			Rarity depth = DraftRules.rarity(p);
			weights[i] = odds.get(depth) / count.get(depth) * DraftRules.attributeWeight(p, attribute) * duplicateWeight(sheet.owned(pool.get(i)));
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
	 * Deals a hand of rolled offers for {@code skill} (§3): up to three different perks, one of them (sometimes) a
	 * wildcard from a skill in {@code recent}, each rolled from its own seed. Perks in {@code avoid} (the hand a reroll
	 * threw back) are dealt only when nothing else is left. An empty hand means nothing can be dealt yet: the draft waits.
	 */
	public static List<Card> deal(ResourceKey<Skill> skill, Sheet sheet, DraftRules.Deck deck, Data data, Random random,
		List<ResourceKey<Skill>> recent, Set<ResourceKey<Perk>> avoid) {
		List<ResourceKey<Perk>> own = candidates(skill, sheet, deck);
		boolean wild = random.nextDouble() < DraftRules.WILDCARD_CHANCE;
		long wildPick = random.nextLong();
		long[] rollSeeds = {random.nextLong(), random.nextLong(), random.nextLong()};
		if (own.isEmpty()) {
			return List.of();
		}
		List<Card> hand = new ArrayList<>(DraftRules.HAND);
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
				ResourceKey<Perk> key = draw(candidates(from, sheet, deck), sheet.level(from), sheet.attribute(from), sheet, deck, r, taken, avoid);
				if (key != null) {
					wildcard = roll(key, deck.get(key).orElseThrow(), sheet.level(from), data, new Random(rollSeeds[2]), true);
					taken.add(key);
				}
			}
		}
		int slots = wildcard == null ? DraftRules.HAND : DraftRules.HAND - 1;
		for (int i = 0; i < slots; i++) {
			ResourceKey<Perk> key = draw(own, sheet.level(skill), sheet.attribute(skill), sheet, deck, random, taken, avoid);
			if (key == null) {
				break;
			}
			hand.add(roll(key, deck.get(key).orElseThrow(), sheet.level(skill), data, new Random(rollSeeds[i]), false));
			taken.add(key);
		}
		if (wildcard != null) {
			hand.add(wildcard);
		}
		return hand;
	}

	/**
	 * Fills a short hand up to {@link DraftRules#HAND} cards with new offers of its own skill (§3.1), keeping the cards it
	 * holds and their places (a Mythic offer stays last). A hand is short when it was dealt while fewer than three cards
	 * of the deck could be dealt (below the tier II gate a skill has only its two roots); when a level opens more of the
	 * deck, the stored hand gets the missing cards instead of staying at two. Never a perk the hand already holds; the
	 * hand is returned unchanged when it is full or nothing new can be dealt.
	 */
	public static List<Card> topUp(List<Card> hand, ResourceKey<Skill> skill, Sheet sheet, DraftRules.Deck deck, Data data, Random random) {
		if (hand.isEmpty() || hand.size() >= DraftRules.HAND) {
			return hand;
		}
		long[] rollSeeds = {random.nextLong(), random.nextLong(), random.nextLong()};
		List<Card> out = new ArrayList<>();
		List<Card> mythic = new ArrayList<>();
		Set<ResourceKey<Perk>> taken = new HashSet<>();
		for (Card c : hand) {
			(c.rarity() == Rarity.MYTHIC ? mythic : out).add(c);
			taken.add(c.perk());
		}
		List<ResourceKey<Perk>> own = candidates(skill, sheet, deck);
		for (int i = 0; out.size() + mythic.size() < DraftRules.HAND; i++) {
			ResourceKey<Perk> key = draw(own, sheet.level(skill), sheet.attribute(skill), sheet, deck, random, taken, Set.of());
			if (key == null) {
				break;
			}
			out.add(roll(key, deck.get(key).orElseThrow(), sheet.level(skill), data, new Random(rollSeeds[i]), false));
			taken.add(key);
		}
		out.addAll(mythic);
		return out.size() == hand.size() ? hand : out;
	}

	/**
	 * The chance, in percent, that one card of a hand dealt now has each frame (§3.2): cursed by depth (a cursed perk
	 * is always Cursed), the rest split by the rolled rarity odds. What the card frame legend shows.
	 */
	public static Map<Rarity, Double> frameOdds(ResourceKey<Skill> skill, Sheet sheet, DraftRules.Deck deck) {
		List<ResourceKey<Perk>> cards = candidates(skill, sheet, deck);
		Map<Rarity, Double> out = new EnumMap<>(Rarity.class);
		for (Rarity r : Rarity.values()) {
			out.put(r, 0.0);
		}
		if (cards.isEmpty()) {
			return out;
		}
		Map<Rarity, Double> depth = DraftRules.odds(sheet.level(skill));
		Map<Rarity, Integer> count = new EnumMap<>(Rarity.class);
		for (ResourceKey<Perk> k : cards) {
			count.merge(DraftRules.rarity(deck.get(k).orElseThrow()), 1, Integer::sum);
		}
		double total = 0;
		double cursed = 0;
		for (ResourceKey<Perk> k : cards) {
			Perk p = deck.get(k).orElseThrow();
			Rarity band = DraftRules.rarity(p);
			double w = depth.get(band) / count.get(band) * DraftRules.attributeWeight(p, sheet.attribute(skill)) * duplicateWeight(sheet.owned(k));
			total += w;
			if (p.kind() == Perk.Kind.CURSED) {
				cursed += w;
			}
		}
		double cursedShare = total <= 0 ? 0 : 100 * cursed / total;
		Map<Rarity, Double> rolled = rarityOdds(sheet.level(skill));
		double sum = rolled.values().stream().mapToDouble(Double::doubleValue).sum();
		for (Rarity r : ROLLED) {
			out.put(r, (100 - cursedShare) * rolled.get(r) / sum);
		}
		out.put(Rarity.CURSED, cursedShare);
		return out;
	}

	/** An offer that is still good: rolled under v2 and its perk can still be dealt. */
	public static boolean valid(Card card, Sheet sheet, DraftRules.Deck deck) {
		if (card.rarity() == Rarity.MYTHIC) {
			// A Mythic offer stays until kept; whether it still waits in the world is checked when it is.
			return true;
		}
		Optional<Perk> perk = deck.get(card.perk());
		return card.rolled() && perk.isPresent() && dealable(card.perk(), perk.get(), sheet, deck).isEmpty();
	}

	// ---------------------------------------------------------------- slots

	/** Base deck slots at a skill level (§5.1): none below 2, then 3 + one every three levels, at most 8. */
	public static int baseSlots(int level) {
		CardConfig c = CardConfig.get();
		if (level < c.slots_from) {
			return 0;
		}
		return Math.min(c.slots_max, c.slots_base + (level - c.slots_from) / c.slots_every);
	}

	/** Slots Wits adds: +1 at Wits 8, +2 at Wits 14 (only in a skill that has slots at all). */
	public static int witsSlots(int wits) {
		int n = 0;
		for (int gate : CardConfig.get().wits_slots) {
			if (wits >= gate) {
				n++;
			}
		}
		return n;
	}

	public static int slots(int level, int wits) {
		int base = baseSlots(level);
		return base == 0 ? 0 : base + witsSlots(wits);
	}

	/** The skill level at which the next base slot opens, or 0 when they are all open. */
	public static int nextSlotAt(int level) {
		for (int l = level + 1; l <= Curves.SKILL_CAP; l++) {
			if (baseSlots(l) > baseSlots(level)) {
				return l;
			}
		}
		return 0;
	}

	/** Why a card can't go in a slot (lang {@code mettle.cards.refused.<name>}). */
	public enum Refusal {
		NO_CARD, NOT_IN_BINDER, ATTUNING, NEEDS_LEVEL, NO_SLOT, SLOT_TAKEN, SAME_PERK, ONE_MASTERY, MASTERY_SIDE, NOT_SLOTTED, NO_FREE_WAY,
		BINDER_FULL, NOT_AT_SHRINE, ONE_MYTHIC_A_DECK, TWO_MYTHICS, MYTHIC, NOT_ONE_PERK, TOP_RARITY, NO_DRAUGHT, CANT_PAY, OVER_CAP, WRONG_SKILL, NOT_ENOUGH_CARDS, MIXED_SKILLS, REROLLS_FULL, PINS_TODAY, BAD_PINS;

		public String translationKey() {
			return "mettle.cards.refused." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Index of the first free slot in a deck, or -1 when every slot is taken. */
	public static int firstFree(Collection<CardData> deck, int slots) {
		Set<Integer> used = new HashSet<>();
		deck.forEach(c -> used.add(c.place().index()));
		for (int i = 0; i < slots; i++) {
			if (!used.contains(i)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Whether {@code card} may go in slot {@code index} (-1: the first free one) of its skill's deck (§5.2): it is in the
	 * binder and attuned, the skill is at its requirement, the slot exists and is free, no card of its perk is slotted,
	 * and a Mastery is the deck's only one and has two cards of its side slotted (any two path cards for the middle one).
	 */
	public static Optional<Refusal> canSlot(CardData card, int index, List<CardData> deckCards, int level, int wits, long gameTime,
		Function<ResourceKey<Perk>, Optional<Perk>> perks) {
		return canSlot(card, index, deckCards, level, wits, gameTime, perks, List.of(), 0);
	}

	/**
	 * {@link #canSlot} knowing every slotted card ({@code allSlotted}, for the Mythic limits: two slotted, never two in one
	 * deck, §5.2) and the extra slots a slotted Polymath gives.
	 */
	public static Optional<Refusal> canSlot(CardData card, int index, List<CardData> deckCards, int level, int wits, long gameTime,
		Function<ResourceKey<Perk>, Optional<Perk>> perks, List<CardData> allSlotted, int extraSlots) {
		if (card.place().kind() != CardData.PlaceKind.BINDER) {
			return Optional.of(Refusal.NOT_IN_BINDER);
		}
		if (!card.attuned(gameTime)) {
			return Optional.of(Refusal.ATTUNING);
		}
		if (level < card.needs()) {
			return Optional.of(Refusal.NEEDS_LEVEL);
		}
		if (card.mythic()) {
			if (deckCards.stream().anyMatch(CardData::mythic)) {
				return Optional.of(Refusal.ONE_MYTHIC_A_DECK);
			}
			if (allSlotted.stream().filter(CardData::mythic).count() >= CardConfig.get().mythic_slots) {
				return Optional.of(Refusal.TWO_MYTHICS);
			}
		}
		int base = slots(level, wits);
		int slots = base == 0 ? 0 : base + extraSlots;
		int at = index < 0 ? firstFree(deckCards, slots) : index;
		if (at < 0 || at >= slots) {
			return Optional.of(Refusal.NO_SLOT);
		}
		for (CardData c : deckCards) {
			if (c.place().index() == at) {
				return Optional.of(Refusal.SLOT_TAKEN);
			}
			if (c.perk().equals(card.perk())) {
				return Optional.of(Refusal.SAME_PERK);
			}
		}
		Optional<Perk> perk = perks.apply(card.perk());
		if (perk.isPresent() && perk.get().kind() == Perk.Kind.MASTERY) {
			for (CardData c : deckCards) {
				if (perks.apply(c.perk()).map(p -> p.kind() == Perk.Kind.MASTERY).orElse(false)) {
					return Optional.of(Refusal.ONE_MASTERY);
				}
			}
			if (!masterySideSlotted(perk.get(), deckCards, Set.of(), perks)) {
				return Optional.of(Refusal.MASTERY_SIDE);
			}
		}
		return Optional.empty();
	}

	/** Two path cards of the Mastery's side (any two for the middle one) among {@code deck}, leaving out {@code ignore}. */
	static boolean masterySideSlotted(Perk mastery, Collection<CardData> deck, Set<Long> ignore, Function<ResourceKey<Perk>, Optional<Perk>> perks) {
		int n = 0;
		for (CardData c : deck) {
			if (ignore.contains(c.id())) {
				continue;
			}
			Optional<Perk> p = perks.apply(c.perk());
			if (p.isPresent() && p.get().kind() == Perk.Kind.PATH && ("any".equals(mastery.side()) || p.get().path().equals(mastery.side()))) {
				n++;
			}
		}
		return n >= 2;
	}

	// ---------------------------------------------------------------- what a deck makes active

	/** The attribute that boosts each trait kind, as a character has it. */
	public interface Attributes {
		int value(CharacterAttribute attribute);
	}

	/** A trait value × (1 + 1.5 % per point of its attribute above 3) (§4.3); points below 3 never shrink it. */
	public static double boost(double value, int attribute) {
		return value * (1 + CardConfig.get().trait_boost_per_point * Math.max(0, attribute - CharacterAttribute.BASE));
	}

	/** One card's most for a trait: its max × the card-cap boost (a broad 12 % reaches 15 %). */
	public static double cardCap(Trait trait) {
		return round(trait.max() * CardConfig.get().card_cap_boost);
	}

	/**
	 * One trait's most from every card together: twice its card cap (§4.1), from the unrounded card cap (a 1.5 max gives
	 * 1.875 a card, 3.75 in all, not twice the printed 1.88).
	 */
	public static double totalCap(Trait trait) {
		return round(trait.max() * CardConfig.get().card_cap_boost * CardConfig.get().trait_total_cap);
	}

	/** One card's effective value for a trait, for this character: boosted, then capped at the card cap. */
	public static double effective(Trait trait, double rolled, Attributes attributes) {
		return Math.min(cardCap(trait), round(boost(rolled, attributes.value(trait.kind().attribute))));
	}

	/**
	 * Everything a set of slotted cards makes true (§5.3, §6): which are dormant, the perks active and their ranks, each
	 * trait's total, each keyword's count, and the active sets with their tiers.
	 *
	 * @param order the keywords in the order they were first counted, extended here (ties among sets go to the first)
	 */
	public record Derived(List<Identifier> order, Map<ResourceKey<Perk>, Integer> active, List<Long> dormant, Map<Identifier, Double> traits,
		Map<Identifier, Integer> sets, Map<Identifier, Integer> tiers) {
	}

	public static Derived derive(List<CardData> cards, ToIntFunction<ResourceKey<Skill>> levels, int wits, Attributes attributes,
		Function<ResourceKey<Perk>, Optional<Perk>> perks, Data data, List<Identifier> pins, List<Identifier> order) {
		// Dormancy: a slot beyond the deck's slots (Wits lost), a card above the skill's level, a second Mastery, a
		// Mastery without two cards of its side (counted among cards that are not dormant themselves).
		Map<ResourceKey<Skill>, List<CardData>> decks = new TreeMap<>(Comparator.comparing(k -> k.identifier().toString()));
		for (CardData c : cards) {
			if (c.slotted()) {
				decks.computeIfAbsent(c.place().deck().orElse(c.skill()), k -> new ArrayList<>()).add(c);
			}
		}
		Set<Long> dormant = new HashSet<>();
		int extra = extraSlots(cards);
		for (Map.Entry<ResourceKey<Skill>, List<CardData>> e : decks.entrySet()) {
			int level = levels.applyAsInt(e.getKey());
			int base = slots(level, wits);
			int slots = base == 0 ? 0 : base + extra;
			List<CardData> deck = e.getValue();
			deck.sort(Comparator.comparingInt(c -> c.place().index()));
			for (CardData c : deck) {
				if (c.place().index() >= slots || level < c.needs()) {
					dormant.add(c.id());
				}
			}
			boolean masterySeen = false;
			for (CardData c : deck) {
				Optional<Perk> p = perks.apply(c.perk());
				if (p.isEmpty() || p.get().kind() != Perk.Kind.MASTERY || dormant.contains(c.id())) {
					continue;
				}
				if (masterySeen || !masterySideSlotted(p.get(), deck, dormant, perks)) {
					dormant.add(c.id());
				} else {
					masterySeen = true;
				}
			}
		}
		Map<ResourceKey<Perk>, Integer> active = new HashMap<>();
		Map<Identifier, Double> traits = new TreeMap<>(Comparator.comparing(Identifier::toString));
		Map<Identifier, Integer> sets = new TreeMap<>(Comparator.comparing(Identifier::toString));
		Map<Identifier, Set<ResourceKey<Skill>>> setSkills = new HashMap<>();
		for (CardData c : cards) {
			if (!c.slotted() || dormant.contains(c.id())) {
				continue;
			}
			active.merge(c.perk(), c.rank(), Math::max);
			for (TraitRoll t : c.traits()) {
				data.trait(t.trait()).ifPresent(def -> traits.merge(t.trait(), effective(def, t.value(), attributes), Double::sum));
			}
			for (Identifier k : c.keywords()) {
				sets.merge(k, 1, Integer::sum);
				setSkills.computeIfAbsent(k, x -> new HashSet<>()).add(c.place().deck().orElse(c.skill()));
			}
		}
		traits.replaceAll((id, v) -> data.trait(id).map(def -> Math.min(totalCap(def), round(v))).orElse(round(v)));
		List<Identifier> nextOrder = new ArrayList<>(order);
		sets.keySet().stream().filter(k -> !nextOrder.contains(k)).forEach(nextOrder::add);
		Map<Identifier, Integer> tiers = new LinkedHashMap<>();
		List<Identifier> chosen = activeSets(sets, setSkills, pins, nextOrder);
		for (Identifier k : chosen) {
			int tier = tier(sets.getOrDefault(k, 0), setSkills.getOrDefault(k, Set.of()).size());
			if (tier > 0) {
				tiers.put(k, tier);
			}
		}
		List<Long> dormantList = new ArrayList<>(dormant);
		dormantList.sort(Long::compare);
		return new Derived(nextOrder, active, dormantList, traits, sets, tiers);
	}

	/** The Polymath Mythic, slotted anywhere, gives every deck one more slot (it pays for its own). */
	public static final Identifier POLYMATH = Identifier.fromNamespaceAndPath("mettle", "polymath");

	public static int extraSlots(List<CardData> cards) {
		for (CardData c : cards) {
			if (c.slotted() && c.mythicId().map(POLYMATH::equals).orElse(false)) {
				return 1;
			}
		}
		return 0;
	}

	/** The tier a keyword reaches (§6.1): 2, 4, 6 cards; the 6 needs cards from two decks. */
	public static int tier(int count, int skills) {
		if (count >= 6 && skills >= 2) {
			return 6;
		}
		return count >= 4 ? 4 : count >= 2 ? 2 : 0;
	}

	/**
	 * The active sets (§6.1): the pinned ones if the player pinned any, else the keywords with the most slotted cards
	 * (ties: the one counted first), at most {@code active_sets}; only keywords with a tier.
	 */
	public static List<Identifier> activeSets(Map<Identifier, Integer> sets, Map<Identifier, Set<ResourceKey<Skill>>> skills, List<Identifier> pins,
		List<Identifier> order) {
		int max = CardConfig.get().active_sets;
		if (!pins.isEmpty()) {
			return pins.stream().limit(max).toList();
		}
		List<Identifier> ranked = new ArrayList<>(sets.keySet());
		ranked.removeIf(k -> tier(sets.get(k), skills.getOrDefault(k, Set.of()).size()) == 0);
		ranked.sort(Comparator.comparingInt((Identifier k) -> -sets.get(k)).thenComparingInt(k -> order.indexOf(k) < 0 ? Integer.MAX_VALUE : order.indexOf(k)));
		return ranked.stream().limit(max).toList();
	}

	// ---------------------------------------------------------------- printed ids

	/** Crockford's base 32: no I, L, O or U, so an id read aloud is never misread. */
	private static final String BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final long MASK30 = (1L << 30) - 1;

	/**
	 * A card's printed id (§2): the skill prefix and six base-32 characters, {@code MN-7Q4K-2F}. The counter is scrambled
	 * by a bijection on 30 bits, so neighbouring cards don't read as neighbours and two ids never print the same; ids past
	 * 2^30 print longer.
	 */
	public static String printId(String prefix, long id) {
		long n = id <= MASK30 ? scramble(id) : id;
		StringBuilder digits = new StringBuilder();
		do {
			digits.append(BASE32.charAt((int) (n & 31)));
			n >>>= 5;
		} while (n > 0);
		while (digits.length() < 6) {
			digits.append('0');
		}
		String s = digits.reverse().toString();
		return prefix + "-" + s.substring(0, s.length() - 2) + "-" + s.substring(s.length() - 2);
	}

	/** A bijection on 30 bits (each step is invertible mod 2^30). */
	static long scramble(long x) {
		x &= MASK30;
		x = (x * 0x2545F491L) & MASK30;
		x ^= x >>> 15;
		x = (x * 0x1B873593L) & MASK30;
		x ^= x >>> 13;
		x = (x * 0x0CC9E2D5L) & MASK30;
		return x;
	}

	// ---------------------------------------------------------------- the v1 → v2 migration (§12)

	/** One card a migration makes: a v1 perk turned card, already rolled, slotted or not. */
	public record Migrated(ResourceKey<Perk> perk, Perk def, Card card, int index) {
		public boolean slotted() {
			return this.index >= 0;
		}
	}

	/** A whole migration: the cards, and the drafts that come back per skill (a rank-r root gives back r − 1). */
	public record Migration(List<Migrated> cards, Map<ResourceKey<Skill>, Integer> drafts) {
	}

	/**
	 * Turns every drafted perk held into one card (§12): a root at rank r becomes that rank's rarity; tier II Rare; tier
	 * III and Masteries Epic; cursed Cursed. Each needs only its tier's level. Traits and the second keyword are rolled
	 * fresh from the seed. Then each skill's deck is filled deepest first, the Mastery only if two of its side fit.
	 */
	public static Migration migrate(Map<ResourceKey<Perk>, Integer> held, DraftRules.Deck deck, Data data, long seed,
		ToIntFunction<ResourceKey<Skill>> levels, int wits) {
		List<ResourceKey<Perk>> keys = held.keySet().stream().filter(k -> deck.get(k).map(p -> p.kind().drafted()).orElse(false))
			.sorted(Comparator.comparing(k -> k.identifier().toString())).toList();
		Map<ResourceKey<Skill>, Integer> drafts = new TreeMap<>(Comparator.comparing(k -> k.identifier().toString()));
		Map<ResourceKey<Skill>, List<Migrated>> bySkill = new TreeMap<>(Comparator.comparing(k -> k.identifier().toString()));
		Random random = new Random(seed ^ 0x5DEECE66DL);
		for (ResourceKey<Perk> key : keys) {
			Perk p = deck.get(key).orElseThrow();
			int r = held.get(key);
			Rarity rarity = switch (p.kind()) {
				case CURSED -> Rarity.CURSED;
				case ROOT -> rarityOfRank(r);
				case MASTERY -> Rarity.EPIC;
				default -> p.tier() >= 3 ? Rarity.EPIC : Rarity.RARE;
			};
			long cardSeed = random.nextLong();
			Random cr = new Random(cardSeed);
			int count = traitCount(rarity, cr);
			boolean second = cr.nextDouble() < secondKeywordChance(rarity);
			List<TraitRoll> traits = rollTraits(data.traits(p.skill()), rarity, count, new Random(cr.nextLong()));
			List<Identifier> keywords = keywords(key, p, data, second, new Random(cr.nextLong()));
			Card card = new Card(key, rank(p, rarity), rarity, false, keywords, traits, migratedNeeds(p));
			bySkill.computeIfAbsent(p.skill(), s -> new ArrayList<>()).add(new Migrated(key, p, card, -1));
			if (p.kind() == Perk.Kind.ROOT && r > 1) {
				drafts.merge(p.skill(), r - 1, Integer::sum);
			}
		}
		List<Migrated> out = new ArrayList<>();
		for (Map.Entry<ResourceKey<Skill>, List<Migrated>> e : bySkill.entrySet()) {
			out.addAll(autoSlot(e.getValue(), slots(levels.applyAsInt(e.getKey()), wits)));
		}
		return new Migration(out, drafts);
	}

	/** Deepest first, up to the slots; the Mastery only if two cards of its side fit with it. */
	static List<Migrated> autoSlot(List<Migrated> cards, int slots) {
		Comparator<Migrated> deepest = Comparator.comparingInt((Migrated m) -> -m.def().tier()).thenComparing(m -> m.perk().identifier().toString());
		List<Migrated> others = cards.stream().filter(m -> m.def().kind() != Perk.Kind.MASTERY).sorted(deepest).toList();
		List<Migrated> masteries = cards.stream().filter(m -> m.def().kind() == Perk.Kind.MASTERY).sorted(deepest).toList();
		List<Migrated> chosen = new ArrayList<>();
		if (!masteries.isEmpty() && slots >= 3) {
			Migrated m = masteries.getFirst();
			List<Migrated> side = others.stream().filter(o -> o.def().kind() == Perk.Kind.PATH
				&& ("any".equals(m.def().side()) || o.def().path().equals(m.def().side()))).limit(2).toList();
			if (side.size() == 2) {
				chosen.add(m);
				chosen.addAll(side);
			}
		}
		for (Migrated o : others) {
			if (chosen.size() >= slots) {
				break;
			}
			if (!chosen.contains(o)) {
				chosen.add(o);
			}
		}
		List<Migrated> out = new ArrayList<>();
		int index = 0;
		for (Migrated m : chosen) {
			out.add(new Migrated(m.perk(), m.def(), m.card(), index++));
		}
		for (Migrated m : cards) {
			if (!chosen.contains(m)) {
				out.add(m);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- rebuilding (§7)

	/** The fee band of a card's requirement: 0 for 2–5, 1 for 6–11, 2 for 12–17, 3 for 18–20. */
	public static int feeBand(int needs) {
		return needs >= 18 ? 3 : needs >= 12 ? 2 : needs >= 6 ? 1 : 0;
	}

	/** The rebuild fee for taking one card out, in Hearth bits (§7.1). */
	public static int feeBits(int needs) {
		return CardConfig.get().rebuild_fees[feeBand(needs)];
	}

	/** The same without Hearth, in emeralds. */
	public static int feeEmeralds(int needs) {
		return CardConfig.get().rebuild_emeralds[feeBand(needs)];
	}

	/**
	 * What a rebuild does to one card (§7.2): the trait its scar takes (empty when it has none left, or a city master's
	 * hand spared it), decided by the card's own seed and scar count, so the preview always equals the scar.
	 *
	 * @param spared a city master's hand passed this card over (one in two)
	 */
	public record Scar(java.util.Optional<TraitRoll> loses, boolean spared) {
	}

	public static Scar scar(CardData card, boolean master) {
		if (card.mythic()) {
			// Mythics are never scarred (§9.1).
			return new Scar(java.util.Optional.empty(), true);
		}
		Random r = new Random(card.scarSeed() * 0x9E3779B97F4A7C15L + card.scars() * 0xC2B2AE3D27D4EB4FL + 1);
		double spare = r.nextDouble();
		int pick = r.nextInt(1 << 16);
		if (master && spare < CardConfig.get().city_master_scar_chance) {
			return new Scar(java.util.Optional.empty(), true);
		}
		if (card.traits().isEmpty()) {
			return new Scar(java.util.Optional.empty(), false);
		}
		return new Scar(java.util.Optional.of(card.traits().get(pick % card.traits().size())), false);
	}

	/** The card after its scar: one trait fewer (none when spared or bare), one more scar mark unless spared. */
	public static CardData scarred(CardData card, boolean master) {
		Scar s = scar(card, master);
		if (s.spared()) {
			return card;
		}
		List<TraitRoll> left = new ArrayList<>(card.traits());
		s.loses().ifPresent(left::remove);
		return card.withTraits(left, card.scars() + 1);
	}

	/** Why these binder cards can't be released for a reroll (§7.5), or empty. */
	public static Optional<Refusal> canRelease(List<CardData> cards, int rerolls, long gameTime) {
		if (cards.size() != CardConfig.get().release_per_reroll) {
			return Optional.of(Refusal.NOT_ENOUGH_CARDS);
		}
		if (rerolls >= DraftRules.MAX_REROLLS) {
			return Optional.of(Refusal.REROLLS_FULL);
		}
		ResourceKey<Skill> skill = cards.getFirst().skill();
		for (CardData c : cards) {
			if (c.mythic()) {
				return Optional.of(Refusal.MYTHIC);
			}
			if (c.place().kind() != CardData.PlaceKind.BINDER) {
				return Optional.of(Refusal.NOT_IN_BINDER);
			}
			if (!c.attuned(gameTime)) {
				return Optional.of(Refusal.ATTUNING);
			}
			if (!c.skill().equals(skill)) {
				return Optional.of(Refusal.MIXED_SKILLS);
			}
		}
		return new HashSet<>(cards.stream().map(CardData::id).toList()).size() == cards.size() ? Optional.empty() : Optional.of(Refusal.NOT_ENOUGH_CARDS);
	}

	// ---------------------------------------------------------------- fusion (§18.1, rulings 2–4)

	/** The rarity three cards fuse into: Common → Rare → Epic; Epic, Cursed and Mythic cards don't fuse. */
	public static Optional<Rarity> fusesInto(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> Optional.of(Rarity.RARE);
			case RARE -> Optional.of(Rarity.EPIC);
			default -> Optional.empty();
		};
	}

	/** Why these cards can't fuse, or empty: three binder cards, attuned, one perk, one rarity that fuses. */
	public static Optional<Refusal> canFuse(List<CardData> cards, long gameTime) {
		if (cards.size() != 3 || new HashSet<>(cards.stream().map(CardData::id).toList()).size() != 3) {
			return Optional.of(Refusal.NOT_ENOUGH_CARDS);
		}
		CardData first = cards.getFirst();
		for (CardData c : cards) {
			if (c.mythic()) {
				return Optional.of(Refusal.MYTHIC);
			}
			if (c.place().kind() != CardData.PlaceKind.BINDER) {
				return Optional.of(Refusal.NOT_IN_BINDER);
			}
			if (!c.attuned(gameTime)) {
				return Optional.of(Refusal.ATTUNING);
			}
			if (!c.perk().equals(first.perk()) || c.rarity() != first.rarity()) {
				return Optional.of(Refusal.NOT_ONE_PERK);
			}
		}
		return fusesInto(first.rarity()).isPresent() ? Optional.empty() : Optional.of(Refusal.TOP_RARITY);
	}

	/** The best trait among the cards: the highest share of its card cap, at its rolled value (ruling 3). */
	public static Optional<TraitRoll> bestTrait(List<CardData> cards, Data data) {
		TraitRoll best = null;
		double share = -1;
		for (CardData c : cards) {
			for (TraitRoll t : c.traits()) {
				Optional<Trait> def = data.trait(t.trait());
				double s = def.map(d -> t.value() / cardCap(d)).orElse(0.0);
				if (s > share) {
					share = s;
					best = t;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * The card three copies fuse into (§18.1): the next rarity, fresh traits and keywords rolled at it, the best trait of
	 * the three kept at its own value (it replaces a fresh roll of the same trait, or the last fresh trait).
	 */
	public static Card fuse(List<CardData> cards, Perk perk, Data data, Random random) {
		CardData first = cards.getFirst();
		Rarity rarity = fusesInto(first.rarity()).orElseThrow();
		Card fresh = roll(first.perk(), perk, rarity, data, random, false);
		List<TraitRoll> traits = new ArrayList<>(fresh.traits());
		Optional<TraitRoll> best = bestTrait(cards, data);
		if (best.isPresent()) {
			int same = -1;
			for (int i = 0; i < traits.size(); i++) {
				if (traits.get(i).trait().equals(best.get().trait())) {
					same = i;
				}
			}
			if (same >= 0) {
				traits.set(same, best.get());
			} else if (traits.isEmpty()) {
				traits.add(best.get());
			} else {
				traits.set(traits.size() - 1, best.get());
			}
		}
		return new Card(fresh.perk(), fresh.rank(), rarity, false, fresh.keywords(), traits, fresh.needs());
	}

	// ---------------------------------------------------------------- the ledger and the cache

	/** A player's cache made to agree with the ledger: what it holds now, and what had to be dropped or put back. */
	public record Reconciled(List<CardData> cards, List<Long> dropped, List<Long> restored) {
		public boolean changed() {
			return !this.dropped.isEmpty() || !this.restored.isEmpty();
		}
	}

	/**
	 * The ledger is the truth (§2.1): a cached id the ledger gives to someone else (or released) is dropped; a card the
	 * ledger gives this player and the cache lacks is put back. Cards both hold are taken from the ledger.
	 */
	public static Reconciled reconcile(List<CardData> cache, List<CardData> ledger) {
		Set<Long> mine = new HashSet<>();
		ledger.forEach(c -> mine.add(c.id()));
		Set<Long> cached = new HashSet<>();
		cache.forEach(c -> cached.add(c.id()));
		List<Long> dropped = cache.stream().map(CardData::id).filter(id -> !mine.contains(id)).sorted().toList();
		List<Long> restored = ledger.stream().map(CardData::id).filter(id -> !cached.contains(id)).sorted().toList();
		List<CardData> cards = new ArrayList<>(ledger);
		cards.sort(Comparator.comparingLong(CardData::id));
		return new Reconciled(cards, dropped, restored);
	}

	// ---------------------------------------------------------------- helpers

	public static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
