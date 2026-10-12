package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * A player's character, as saved and synced to their own client. Immutable: Fabric only syncs an attachment when a new
 * value that is not {@code equals} the old one is set, so every change builds a new record (use the {@code with*}
 * methods).
 *
 * @param background the chosen background; empty until the player picks one
 * @param spent      attribute points the player placed (the Wanderer's free points, then points from character levels)
 * @param favoured   skills the player picked to favour (only for backgrounds that let them pick)
 * @param xp         XP per skill
 * @param perks      perks held, with their rank (drafted and earned alike)
 * @param draft      the perk drafts (docs/DRAFT.md); empty for a character saved before drafts, which the server migrates on
 *                   join (unspent perk points become drafts)
 */
public record CharacterState(
	Optional<ResourceKey<Background>> background,
	Map<CharacterAttribute, Integer> spent,
	List<ResourceKey<Skill>> favoured,
	Map<ResourceKey<Skill>, Double> xp,
	Map<ResourceKey<Perk>, Integer> perks,
	Optional<DraftState> draft
) {
	public static final CharacterState EMPTY = new CharacterState(Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), Optional.empty());

	public static final Codec<CharacterState> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceKey.codec(MettleRegistries.BACKGROUND).optionalFieldOf("background").forGetter(CharacterState::background),
		MettleCodecs.lenientMap(CharacterAttribute.CODEC, MettleCodecs.clampedInt(0, CharacterAttribute.MAX), "placed attribute point")
			.optionalFieldOf("spent", Map.of()).forGetter(CharacterState::spent),
		ResourceKey.codec(MettleRegistries.SKILL).listOf().optionalFieldOf("favoured", List.of()).forGetter(CharacterState::favoured),
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.SKILL), Codec.DOUBLE, "skill XP").optionalFieldOf("xp", Map.of()).forGetter(CharacterState::xp),
		MettleCodecs.lenientMap(ResourceKey.codec(MettleRegistries.PERK), MettleCodecs.clampedInt(1, 5), "perk rank").optionalFieldOf("perks", Map.of())
			.forGetter(CharacterState::perks),
		DraftState.CODEC.optionalFieldOf("draft").forGetter(CharacterState::draft)
	).apply(i, CharacterState::new));

	/** Every value kept in range whatever made it (a command, an old save), so the record always saves (MettleCodecs). */
	public CharacterState {
		Map<CharacterAttribute, Integer> s = new HashMap<>();
		spent.forEach((a, v) -> s.put(a, Math.clamp(v, 0, CharacterAttribute.MAX)));
		spent = Map.copyOf(s);
		Map<ResourceKey<Perk>, Integer> p = new HashMap<>();
		perks.forEach((k, v) -> {
			if (v > 0) {
				p.put(k, Math.min(v, 5));
			}
		});
		perks = Map.copyOf(p);
		Map<ResourceKey<Skill>, Double> x = new HashMap<>();
		xp.forEach((k, v) -> x.put(k, Double.isFinite(v) ? Math.max(0, v) : 0));
		xp = Map.copyOf(x);
		favoured = List.copyOf(favoured);
	}

	/**
	 * What the owner's client is sent: the record without the draft seed (it, with the world's secret, decides every future
	 * hand; the client only needs the hands already dealt).
	 */
	public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, CharacterState> SYNC_CODEC =
		net.minecraft.network.codec.ByteBufCodecs.fromCodecTrusted(CODEC).map(s -> s, CharacterState::forClient);

	/** This record as its owner's client sees it (no draft seed). */
	public CharacterState forClient() {
		return this.draft.isEmpty() || this.draft.get().seed() == 0 ? this : withDraft(this.draft.get().withSeed(0));
	}

	public CharacterState(Optional<ResourceKey<Background>> background, Map<CharacterAttribute, Integer> spent, List<ResourceKey<Skill>> favoured) {
		this(background, spent, favoured, Map.of(), Map.of(), Optional.empty());
	}

	public CharacterState(Optional<ResourceKey<Background>> background, Map<CharacterAttribute, Integer> spent, List<ResourceKey<Skill>> favoured,
		Map<ResourceKey<Skill>, Double> xp, Map<ResourceKey<Perk>, Integer> perks) {
		this(background, spent, favoured, xp, perks, Optional.empty());
	}

	public boolean hasBackground() {
		return this.background.isPresent();
	}

	/** The attribute's value: base 3, plus the background's bonus, plus what the player placed. */
	public int attribute(CharacterAttribute attribute, @Nullable Background background) {
		int bonus = background == null ? 0 : background.bonus(attribute);
		return Math.clamp(CharacterAttribute.BASE + bonus + this.spent.getOrDefault(attribute, 0), CharacterAttribute.MIN, CharacterAttribute.MAX);
	}

	public double xp(ResourceKey<Skill> skill) {
		return this.xp.getOrDefault(skill, 0.0);
	}

	public int level(ResourceKey<Skill> skill) {
		return Curves.skillLevel(xp(skill));
	}

	public double totalXp() {
		return this.xp.values().stream().mapToDouble(Double::doubleValue).sum();
	}

	public int characterLevel() {
		return Curves.characterLevel(totalXp());
	}

	public int rank(ResourceKey<Perk> perk) {
		return this.perks.getOrDefault(perk, 0);
	}

	/** Skills the character favours: picked ones, or the background's own. */
	public List<ResourceKey<Skill>> favouredSkills(@Nullable Background background) {
		return !this.favoured.isEmpty() || background == null ? this.favoured : background.favoured();
	}

	public CharacterState withBackground(Optional<ResourceKey<Background>> b, Map<CharacterAttribute, Integer> s, List<ResourceKey<Skill>> f) {
		return new CharacterState(b, s, f, this.xp, this.perks, this.draft);
	}

	public CharacterState withSpent(Map<CharacterAttribute, Integer> s) {
		return new CharacterState(this.background, Map.copyOf(s), this.favoured, this.xp, this.perks, this.draft);
	}

	public CharacterState withXp(ResourceKey<Skill> skill, double value) {
		Map<ResourceKey<Skill>, Double> copy = new HashMap<>(this.xp);
		copy.put(skill, value);
		return new CharacterState(this.background, this.spent, this.favoured, Map.copyOf(copy), this.perks, this.draft);
	}

	public CharacterState withPerks(Map<ResourceKey<Perk>, Integer> p) {
		return new CharacterState(this.background, this.spent, this.favoured, this.xp, Map.copyOf(p), this.draft);
	}

	public CharacterState withDraft(DraftState d) {
		return new CharacterState(this.background, this.spent, this.favoured, this.xp, this.perks, Optional.of(d));
	}

	/** The drafts, or an empty set for a character not yet migrated (the client shows nothing waiting). */
	public DraftState drafts() {
		return this.draft.orElse(DraftState.fresh(0L, 0));
	}
}
