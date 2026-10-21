package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What build feel keeps for a character (docs/BUILD_FEEL.md §7), part of the card cache ({@link CardsState#build}): saved
 * with the player, kept on death, synced to the owner. Immutable; every number is clamped and every list read leniently
 * (MettleCodecs), so a damaged entry never loses the record.
 *
 * @param focus        the Focus keyword (§1.3): counts as {@code focus_weight} slotted cards for momentum, never for sets
 * @param focusDay     the in-game day the Focus last changed (once a day), -1 never
 * @param signatures   each deck's Signature card id (§2.2)
 * @param byname       the byname worn, the tier earned (never drops) and every byname held (§2.1)
 * @param shards       Mythic shards held (§5.2), bound to the character
 * @param forges       forges made (the cost ladder 30 / 45 / 60 climbs with them)
 * @param legend       the legend title's stars (0 = none; §5.3, when no drawn Mythic is left anywhere)
 * @param legendOf     the world the legend title names ("the Legend of …")
 * @param once         shard sources and milestones that happen once per character ("ms/first_set", "shard/skill20/mettle:mining")
 * @param guide        the first-ten-minutes guide (§4)
 */
public record BuildState(
	Optional<Identifier> focus,
	long focusDay,
	Map<ResourceKey<Skill>, Long> signatures,
	Bynames byname,
	int shards,
	int forges,
	int legend,
	String legendOf,
	List<String> once,
	Guide guide
) {
	/** A byname as parts (rendered with the byname word tables on any side): tier, keywords, skills, the Mythic's epithet. */
	public record Byname(int tier, Identifier kw1, Optional<Identifier> kw2, ResourceKey<Skill> skill1, Optional<ResourceKey<Skill>> skill2,
		Optional<Identifier> mythic) {
		public static final Codec<Byname> CODEC = RecordCodecBuilder.create(i -> i.group(
			MettleCodecs.clampedInt(1, 4).fieldOf("tier").forGetter(Byname::tier),
			Identifier.CODEC.fieldOf("kw1").forGetter(Byname::kw1),
			Identifier.CODEC.optionalFieldOf("kw2").forGetter(Byname::kw2),
			ResourceKey.codec(MettleRegistries.SKILL).fieldOf("skill1").forGetter(Byname::skill1),
			ResourceKey.codec(MettleRegistries.SKILL).optionalFieldOf("skill2").forGetter(Byname::skill2),
			Identifier.CODEC.optionalFieldOf("mythic").forGetter(Byname::mythic)
		).apply(i, Byname::new));
	}

	/**
	 * The bynames: the one worn (empty until the first set), the highest tier earned (never drops), and every byname held,
	 * oldest first, at most {@link #KEEP}.
	 */
	public record Bynames(Optional<Byname> worn, int tier, List<Byname> held) {
		public static final int KEEP = 24;
		public static final Bynames NONE = new Bynames(Optional.empty(), 0, List.of());
		public static final Codec<Bynames> CODEC = RecordCodecBuilder.create(i -> i.group(
			Byname.CODEC.optionalFieldOf("worn").forGetter(Bynames::worn),
			MettleCodecs.clampedInt(0, 4).optionalFieldOf("tier", 0).forGetter(Bynames::tier),
			MettleCodecs.lenientList(Byname.CODEC, "held byname").optionalFieldOf("held", List.of()).forGetter(Bynames::held)
		).apply(i, Bynames::new));

		public Bynames {
			tier = Math.clamp(tier, 0, 4);
			held = held.size() > KEEP ? List.copyOf(held.subList(held.size() - KEEP, held.size())) : List.copyOf(held);
		}

		/** Wears {@code b} (keeping it in the held list), with the earned tier at least its tier. */
		public Bynames wearing(Byname b) {
			List<Byname> h = new ArrayList<>(this.held);
			if (!h.contains(b)) {
				h.add(b);
			}
			return new Bynames(Optional.of(b), Math.max(this.tier, b.tier()), h);
		}

		public Bynames withTier(int t) {
			return new Bynames(this.worn, Math.max(this.tier, t), this.held);
		}
	}

	/**
	 * The guide (§4): on for characters made after build feel shipped; the hints already shown; the hint up now (0 none) and
	 * the game time it went up; skipped; whether the guided first hand (a Rare) was dealt.
	 */
	public record Guide(boolean on, List<Integer> seen, int current, long since, boolean skipped, boolean firstHand) {
		public static final Guide OFF = new Guide(false, List.of(), 0, 0, false, false);
		public static final Codec<Guide> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("on", false).forGetter(Guide::on),
			MettleCodecs.lenientList(MettleCodecs.clampedInt(0, 99), "guide hint").optionalFieldOf("seen", List.of()).forGetter(Guide::seen),
			MettleCodecs.clampedInt(0, 99).optionalFieldOf("current", 0).forGetter(Guide::current),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Guide::since),
			Codec.BOOL.optionalFieldOf("skipped", false).forGetter(Guide::skipped),
			Codec.BOOL.optionalFieldOf("first_hand", false).forGetter(Guide::firstHand)
		).apply(i, Guide::new));

		public Guide {
			seen = List.copyOf(seen);
		}

		public boolean seen(int hint) {
			return this.seen.contains(hint);
		}
	}

	/** Once-only entries kept at most (every shard source and milestone a character can reach fits well under it). */
	public static final int KEEP_ONCE = 256;

	public static final BuildState EMPTY = new BuildState(Optional.empty(), -1, Map.of(), Bynames.NONE, 0, 0, 0, "", List.of(), Guide.OFF);

	public static final Codec<BuildState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.optionalFieldOf("focus").forGetter(BuildState::focus),
		Codec.LONG.optionalFieldOf("focus_day", -1L).forGetter(BuildState::focusDay),
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.SKILL), Codec.LONG, "signature").optionalFieldOf("signatures", Map.of())
			.forGetter(BuildState::signatures),
		Bynames.CODEC.optionalFieldOf("byname", Bynames.NONE).forGetter(BuildState::byname),
		MettleCodecs.clampedInt(0, 1_000_000).optionalFieldOf("shards", 0).forGetter(BuildState::shards),
		MettleCodecs.clampedInt(0, 10_000).optionalFieldOf("forges", 0).forGetter(BuildState::forges),
		MettleCodecs.clampedInt(0, 10).optionalFieldOf("legend", 0).forGetter(BuildState::legend),
		Codec.STRING.optionalFieldOf("legend_of", "").forGetter(BuildState::legendOf),
		MettleCodecs.lenientList(Codec.STRING, "build feel entry").optionalFieldOf("once", List.of()).forGetter(BuildState::once),
		Guide.CODEC.optionalFieldOf("guide", Guide.OFF).forGetter(BuildState::guide)
	).apply(i, BuildState::new));

	public BuildState {
		signatures = Map.copyOf(signatures);
		shards = Math.clamp(shards, 0, 1_000_000);
		forges = Math.max(0, forges);
		legend = Math.clamp(legend, 0, 10);
		legendOf = legendOf == null ? "" : legendOf;
		once = once.size() > KEEP_ONCE ? List.copyOf(once.subList(once.size() - KEEP_ONCE, once.size())) : List.copyOf(once);
	}

	public boolean done(String key) {
		return this.once.contains(key);
	}

	public BuildState withOnce(String key) {
		if (done(key)) {
			return this;
		}
		List<String> next = new ArrayList<>(this.once);
		next.add(key);
		return new BuildState(this.focus, this.focusDay, this.signatures, this.byname, this.shards, this.forges, this.legend, this.legendOf, next, this.guide);
	}

	public BuildState withFocus(Optional<Identifier> f, long day) {
		return new BuildState(f, day, this.signatures, this.byname, this.shards, this.forges, this.legend, this.legendOf, this.once, this.guide);
	}

	public BuildState withSignature(ResourceKey<Skill> skill, Optional<Long> card) {
		Map<ResourceKey<Skill>, Long> next = new HashMap<>(this.signatures);
		card.ifPresentOrElse(c -> next.put(skill, c), () -> next.remove(skill));
		return new BuildState(this.focus, this.focusDay, next, this.byname, this.shards, this.forges, this.legend, this.legendOf, this.once, this.guide);
	}

	public BuildState withByname(Bynames b) {
		return new BuildState(this.focus, this.focusDay, this.signatures, b, this.shards, this.forges, this.legend, this.legendOf, this.once, this.guide);
	}

	public BuildState withShards(int n) {
		return new BuildState(this.focus, this.focusDay, this.signatures, this.byname, n, this.forges, this.legend, this.legendOf, this.once, this.guide);
	}

	public BuildState withForge(int shardsLeft, int forgesMade, int stars, String of) {
		return new BuildState(this.focus, this.focusDay, this.signatures, this.byname, shardsLeft, forgesMade, stars, of, this.once, this.guide);
	}

	public BuildState withGuide(Guide g) {
		return new BuildState(this.focus, this.focusDay, this.signatures, this.byname, this.shards, this.forges, this.legend, this.legendOf, this.once, g);
	}
}
