package dev.voidmc.mettle.character;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.character.moment.Moments;
import dev.voidmc.mettle.character.respec.RespecRules;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Holding, earning and refunding perks, and spending attribute points. Server thread. Drafted perks are picked from
 * draft hands ({@link dev.voidmc.mettle.character.draft.Drafts}); {@link #learn} remains only for ops and tests.
 */
public final class Perks {
	private Perks() {
	}

	public static ResourceKey<Perk> key(Identifier id) {
		return ResourceKey.create(MettleRegistries.PERK, id);
	}

	/** The rank a player holds in a perk (0 when not held). Works on both sides. */
	public static int rank(Player player, Identifier perk) {
		return CharacterStore.get(player).rank(key(perk));
	}

	public static boolean has(Player player, Identifier perk) {
		return rank(player, perk) > 0;
	}

	public static Registry<Perk> registry(Player player) {
		return player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK);
	}

	/** Everything {@link PerkRules} needs about one of a player's trees. */
	public static PerkRules.TreeView tree(Player player, ResourceKey<Skill> skillKey) {
		CharacterState state = CharacterStore.get(player);
		Registry<Perk> perks = registry(player);
		Skill skill = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValueOrThrow(skillKey);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>();
		Map<ResourceKey<Perk>, Perk> inTree = new HashMap<>();
		perks.listElements().filter(r -> r.value().skill().equals(skillKey)).forEach(r -> {
			inTree.put(r.key(), r.value());
			int rank = state.rank(r.key());
			if (rank > 0) {
				held.put(r.key(), rank);
			}
		});
		return new PerkRules.TreeView(state.level(skillKey), state.attribute(skill.attribute(), background), state.drafts().queued(skillKey), held, inTree);
	}

	/**
	 * Learns the next rank of a drafted perk without a draft (ops' {@code /mettle perk learn} and tests); the tree's shape
	 * rules still apply ({@link PerkRules#eligible}). Players get perks from drafts. Returns why not, or empty on success.
	 */
	public static Optional<PerkRules.Reason> learn(ServerPlayer player, ResourceKey<Perk> key) {
		Optional<Perk> perk = registry(player).getOptional(key);
		if (perk.isEmpty()) {
			return Optional.of(PerkRules.Reason.NOT_BOUGHT);
		}
		Optional<PerkRules.Reason> blocked = PerkRules.eligible(key, perk.get(), tree(player, perk.get().skill()));
		if (blocked.isPresent()) {
			return blocked;
		}
		CharacterState state = CharacterStore.get(player);
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>(state.perks());
		int rank = held.merge(key, 1, Integer::sum);
		CharacterStore.set(player, state.withPerks(held));
		EffectsApplier.apply(player);
		Component text = perk.get().kind() == Perk.Kind.MASTERY
			? Component.translatable("mettle.feedback.mastery", perk.get().name())
			: Component.translatable("mettle.feedback.learned", perk.get().name(), rank);
		Moments.perk(player, key, perk.get(), rank);
		SignaturePerks.feedback(player, key.identifier(), text);
		MettleEvents.PERK_LEARNED.invoker().learned(player, key, rank);
		// A hand may hold a card for the rank just learned: it is dealt again.
		Drafts.refresh(player);
		return Optional.empty();
	}

	/** Gives an earned perk (feat, synergy, teaching) if not already held. Returns whether it was new. */
	public static boolean grant(ServerPlayer player, ResourceKey<Perk> key) {
		Optional<Perk> perk = registry(player).getOptional(key);
		CharacterState state = CharacterStore.get(player);
		if (perk.isEmpty() || state.rank(key) > 0) {
			return false;
		}
		Map<ResourceKey<Perk>, Integer> held = new HashMap<>(state.perks());
		held.put(key, 1);
		CharacterStore.set(player, state.withPerks(held));
		EffectsApplier.apply(player);
		String kind = perk.get().kind().getSerializedName();
		Moments.perk(player, key, perk.get(), 1);
		SignaturePerks.feedback(player, key.identifier(), Component.translatable("mettle.feedback.earned." + kind, perk.get().name()));
		Mettle.LOGGER.info("{} earned {} {}", player.getName().getString(), kind, key.identifier());
		MettleEvents.PERK_LEARNED.invoker().learned(player, key, 1);
		// A deed is luck too: every feat brings a reroll (docs/DRAFT.md §5).
		if (perk.get().kind() == Perk.Kind.FEAT) {
			Drafts.addRerolls(player, DraftRules.REROLLS_PER_FEAT);
		}
		return true;
	}

	/**
	 * Refunds every drafted perk, each rank back as a draft of its skill (and, with {@code attributes}, every placed
	 * attribute point). Earned perks stay.
	 */
	public static void respec(ServerPlayer player, boolean attributes) {
		CharacterState state = Drafts.ensure(player);
		Registry<Perk> perks = registry(player);
		Map<ResourceKey<Perk>, Integer> kept = new HashMap<>();
		Map<ResourceKey<Perk>, Integer> removed = new HashMap<>();
		state.perks().forEach((k, v) -> {
			if (perks.getOptional(k).map(p -> p.kind().drafted()).orElse(false)) {
				removed.put(k, v);
			} else {
				kept.put(k, v);
			}
		});
		CharacterState next = Drafts.returnDrafts(player.level().registryAccess(), state.withPerks(kept), removed);
		if (attributes) {
			// A Wanderer's free points come back as free points (attributePoints counts them), so placing is all redone.
			next = next.withSpent(Map.of());
		}
		CharacterStore.set(player, next);
		Drafts.refresh(player);
		EffectsApplier.apply(player);
		MettleEvents.RESPEC.invoker().respec(player, attributes);
	}

	/**
	 * Refunds every drafted perk of one skill: roots, path perks, cursed cards and the Mastery (the Draught of Forgetting).
	 * Each rank comes back as a draft of that skill. Earned perks, skill XP and levels stay. Changes nothing when there is
	 * nothing to forget.
	 */
	public static RespecRules.Forgetting forget(ServerPlayer player, ResourceKey<Skill> skill) {
		CharacterState state = Drafts.ensure(player);
		RespecRules.Forgetting result = RespecRules.forget(state.perks(), registry(player)::getOptional, skill);
		if (!result.nothing()) {
			CharacterStore.set(player, state.withPerks(result.kept()).withDraft(state.drafts().addQueued(skill, result.drafts())));
			Drafts.refresh(player);
			EffectsApplier.apply(player);
			Mettle.LOGGER.info("{} forgot {} perks of {} ({} drafts back)", player.getName().getString(), result.perks(), skill.identifier(), result.drafts());
			MettleEvents.RESPEC.invoker().respec(player, false);
		}
		return result;
	}

	/** Returns every placed attribute point to be placed again (the Draught of Unbinding). Returns how many; 0 changes nothing. */
	public static int unbind(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		int placed = RespecRules.placed(state.spent());
		if (placed > 0) {
			CharacterStore.set(player, state.withSpent(Map.of()));
			EffectsApplier.apply(player);
			Mettle.LOGGER.info("{} unbound {} attribute points", player.getName().getString(), placed);
			MettleEvents.RESPEC.invoker().respec(player, true);
		}
		return placed;
	}

	// ---------------------------------------------------------------- attribute points

	/** Attribute points not yet placed: one per character level above 1, plus free and perk points. */
	public static int attributePoints(Player player) {
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		int free = background == null ? 0 : background.freePoints();
		return RespecRules.unplaced(state.characterLevel(), RespecRules.placed(state.spent()), free, bonusPoints(player));
	}

	/** Attribute points granted by perks (Seen It All). Earned, so a respec keeps them. */
	private static int bonusPoints(Player player) {
		return dev.voidmc.mettle.character.skill.survival.GeneralFeats.bonusAttributePoints(player);
	}

	public static boolean spendAttribute(ServerPlayer player, CharacterAttribute attribute) {
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		if (attributePoints(player) <= 0 || state.attribute(attribute, background) >= CharacterAttribute.MAX) {
			return false;
		}
		Map<CharacterAttribute, Integer> spent = new EnumMap<>(CharacterAttribute.class);
		spent.putAll(state.spent());
		spent.merge(attribute, 1, Integer::sum);
		CharacterStore.set(player, state.withSpent(spent));
		EffectsApplier.apply(player);
		return true;
	}

	/** Grants every synergy whose skills are all at their level. Called after a level-up. */
	public static void checkSynergies(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		for (Holder.Reference<Perk> ref : registry(player).listElements().toList()) {
			Perk p = ref.value();
			if (p.kind() == Perk.Kind.SYNERGY && state.rank(ref.key()) == 0 && !p.synergy().isEmpty()
				&& p.synergy().entrySet().stream().allMatch(e -> state.level(e.getKey()) >= e.getValue())) {
				grant(player, ref.key());
			}
		}
	}
}
