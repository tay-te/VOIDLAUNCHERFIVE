package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

/**
 * A player's perk drafts (docs/DRAFT.md), part of the synced character record. Every skill level queues a draft for that
 * skill; the skill's current hand (up to three cards, keep one) is stored here so a reconnect never re-deals it.
 * Immutable like {@link CharacterState}: change it with the {@code with*} methods.
 *
 * @param queued  drafts waiting per skill, the dealt hand included (never expire)
 * @param hands   the hand dealt for each skill with a draft waiting; empty when nothing can be dealt yet
 * @param rerolls rerolls the player holds (one pool for every skill)
 * @param seed    the player's own seed; hands are dealt from it and {@code dealt}, so dealing is deterministic
 * @param dealt   how many hands have been dealt so far (the next hand's place in the seed's sequence)
 * @param recent  skills that gained XP lately, most recent first (wildcards come from these)
 * @param boons   boons waiting per skill (docs/BUILD_FEEL.md §3): odd levels from 7 give one instead of a reroll; they never
 *                expire. An old save has none: the rerolls its odd levels gave stay rerolls.
 */
public record DraftState(
	Map<ResourceKey<Skill>, Integer> queued,
	Map<ResourceKey<Skill>, List<Card>> hands,
	int rerolls,
	long seed,
	int dealt,
	List<ResourceKey<Skill>> recent,
	Map<ResourceKey<Skill>, Integer> boons
) {
	/** How many skills {@code recent} remembers. */
	public static final int RECENT = 4;

	/** A card's rarity: its odds and its frame colour. Cursed cards carry a real downside. */
	public enum Rarity implements StringRepresentable {
		COMMON(0xB9BEC6),
		RARE(0x4DA3F5),
		EPIC(0xB27CFF),
		CURSED(0xD8453B),
		/** A Mythic (docs/CARDS.md §9): no rarity of its own; the white-gold frame (the user's answer of 2026-10-10). */
		MYTHIC(0xF2E6C2);

		public static final StringRepresentable.EnumCodec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);

		/** The frame colour, RGB. */
		public final int colour;

		Rarity(int colour) {
			this.colour = colour;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String hex() {
			return String.format(Locale.ROOT, "#%06X", this.colour);
		}
	}

	/**
	 * One card in a hand, an <b>offer</b> (docs/CARDS.md §2.1): the rolled perk, rarity, rank, keywords, traits and
	 * requirement. It gets an id and enters the world ledger only when kept; rerolled offers never existed. A v1 hand
	 * (stored before cards v2) has no keywords or traits and is dealt again.
	 *
	 * @param rank     roots: the rank the rarity gives (Common I, Rare II, Epic III); 1 otherwise
	 * @param wildcard a card from another skill the player used recently, dealt into this skill's hand
	 * @param needs    the skill level needed to slot it (0 on a v1 offer)
	 */
	public record Card(ResourceKey<Perk> perk, int rank, Rarity rarity, boolean wildcard, List<Identifier> keywords,
		List<CardData.TraitRoll> traits, int needs) {
		public static final Codec<Card> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(MettleRegistries.PERK).fieldOf("perk").forGetter(Card::perk),
			MettleCodecs.clampedInt(1, 5).fieldOf("rank").forGetter(Card::rank),
			Rarity.CODEC.fieldOf("rarity").forGetter(Card::rarity),
			Codec.BOOL.optionalFieldOf("wildcard", false).forGetter(Card::wildcard),
			Identifier.CODEC.listOf().optionalFieldOf("keywords", List.of()).forGetter(Card::keywords),
			CardData.TraitRoll.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Card::traits),
			Codec.INT.optionalFieldOf("needs", 0).forGetter(Card::needs)
		).apply(i, Card::new));

		public Card {
			rank = Math.clamp(rank, 1, 5);
			keywords = List.copyOf(keywords);
			traits = List.copyOf(traits);
		}

		/** A v1 card (the draft before cards): no keywords, traits or requirement. */
		public Card(ResourceKey<Perk> perk, int rank, Rarity rarity, boolean wildcard) {
			this(perk, rank, rarity, wildcard, List.of(), List.of(), 0);
		}

		/** Whether it was rolled under cards v2 (a v1 hand has no keywords and is dealt again). A Mythic offer has none. */
		public boolean rolled() {
			return !this.keywords.isEmpty() || this.rarity == Rarity.MYTHIC;
		}
	}

	/** The most drafts one skill can hold waiting; more are dropped (an op's give, a long career), never an error. */
	public static final int MAX_QUEUED = 1000;

	public static final Codec<DraftState> CODEC = RecordCodecBuilder.create(i -> i.group(
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.SKILL), MettleCodecs.clampedInt(0, MAX_QUEUED), "queued draft").optionalFieldOf("queued", Map.of())
			.forGetter(DraftState::queued),
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.SKILL), MettleCodecs.lenientList(Card.CODEC, "card in a hand"), "hand")
			.optionalFieldOf("hands", Map.of()).forGetter(DraftState::hands),
		Codec.INT.optionalFieldOf("rerolls", 0).forGetter(DraftState::rerolls),
		Codec.LONG.optionalFieldOf("seed", 0L).forGetter(DraftState::seed),
		Codec.INT.optionalFieldOf("dealt", 0).forGetter(DraftState::dealt),
		ResourceKey.codec(MettleRegistries.SKILL).listOf().optionalFieldOf("recent", List.of()).forGetter(DraftState::recent),
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.SKILL), MettleCodecs.clampedInt(0, MAX_QUEUED), "boon").optionalFieldOf("boons", Map.of())
			.forGetter(DraftState::boons)
	).apply(i, DraftState::new));

	public DraftState {
		Map<ResourceKey<Skill>, Integer> q = new HashMap<>();
		queued.forEach((k, v) -> {
			if (v != null && v > 0) {
				q.put(k, Math.min(v, MAX_QUEUED));
			}
		});
		queued = Map.copyOf(q);
		rerolls = Math.max(0, rerolls);
		dealt = Math.max(0, dealt);
		hands = Map.copyOf(hands);
		recent = List.copyOf(recent);
		Map<ResourceKey<Skill>, Integer> b = new HashMap<>();
		boons.forEach((k, v) -> {
			if (v != null && v > 0) {
				b.put(k, Math.min(v, MAX_QUEUED));
			}
		});
		boons = Map.copyOf(b);
	}

	/** A record without boons (every save before build feel). */
	public DraftState(Map<ResourceKey<Skill>, Integer> queued, Map<ResourceKey<Skill>, List<Card>> hands, int rerolls, long seed, int dealt,
		List<ResourceKey<Skill>> recent) {
		this(queued, hands, rerolls, seed, dealt, recent, Map.of());
	}

	public int boons(ResourceKey<Skill> skill) {
		return this.boons.getOrDefault(skill, 0);
	}

	public int totalBoons() {
		return this.boons.values().stream().mapToInt(Integer::intValue).sum();
	}

	public DraftState addBoons(ResourceKey<Skill> skill, int delta) {
		Map<ResourceKey<Skill>, Integer> copy = new HashMap<>(this.boons);
		int n = boons(skill) + delta;
		if (n <= 0) {
			copy.remove(skill);
		} else {
			copy.put(skill, n);
		}
		return new DraftState(this.queued, this.hands, this.rerolls, this.seed, this.dealt, this.recent, copy);
	}

	public static DraftState fresh(long seed, int rerolls) {
		return new DraftState(Map.of(), Map.of(), rerolls, seed, 0, List.of());
	}

	public int queued(ResourceKey<Skill> skill) {
		return this.queued.getOrDefault(skill, 0);
	}

	public List<Card> hand(ResourceKey<Skill> skill) {
		return this.hands.getOrDefault(skill, List.of());
	}

	/** Drafts waiting in every skill. */
	public int totalQueued() {
		return this.queued.values().stream().mapToInt(Integer::intValue).sum();
	}

	public DraftState withQueued(ResourceKey<Skill> skill, int count) {
		Map<ResourceKey<Skill>, Integer> copy = new HashMap<>(this.queued);
		if (count <= 0) {
			copy.remove(skill);
		} else {
			copy.put(skill, count);
		}
		return new DraftState(copy, this.hands, this.rerolls, this.seed, this.dealt, this.recent, this.boons);
	}

	public DraftState addQueued(ResourceKey<Skill> skill, int delta) {
		return withQueued(skill, queued(skill) + delta);
	}

	/** Sets a skill's hand; an empty list removes it. */
	public DraftState withHand(ResourceKey<Skill> skill, List<Card> cards) {
		Map<ResourceKey<Skill>, List<Card>> copy = new HashMap<>(this.hands);
		if (cards.isEmpty()) {
			copy.remove(skill);
		} else {
			copy.put(skill, List.copyOf(cards));
		}
		return new DraftState(this.queued, copy, this.rerolls, this.seed, this.dealt, this.recent, this.boons);
	}

	public DraftState withRerolls(int count) {
		return new DraftState(this.queued, this.hands, Math.max(0, count), this.seed, this.dealt, this.recent, this.boons);
	}

	public DraftState withSeed(long s) {
		return new DraftState(this.queued, this.hands, this.rerolls, s, this.dealt, this.recent, this.boons);
	}

	/** One more hand dealt from the seed. */
	public DraftState nextDeal() {
		return new DraftState(this.queued, this.hands, this.rerolls, this.seed, this.dealt + 1, this.recent, this.boons);
	}

	/** {@code skill} gained XP: it moves to the front of {@link #recent}. Returns this when it already is. */
	public DraftState used(ResourceKey<Skill> skill) {
		if (!this.recent.isEmpty() && this.recent.getFirst().equals(skill)) {
			return this;
		}
		List<ResourceKey<Skill>> next = new ArrayList<>(RECENT);
		next.add(skill);
		for (ResourceKey<Skill> s : this.recent) {
			if (!s.equals(skill) && next.size() < RECENT) {
				next.add(s);
			}
		}
		return new DraftState(this.queued, this.hands, this.rerolls, this.seed, this.dealt, next, this.boons);
	}
}
