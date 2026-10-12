package dev.voidmc.mettle.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * What happens to a character, for compat and other mods (ARCHITECTURE.md §4). All fire on the server thread, after the
 * change is stored.
 */
public final class MettleEvents {
	/** Scale or cancel skill XP before it is added (after Mettle's own multipliers). Return 0 to cancel. */
	public static final Event<ModifyXp> MODIFY_XP = EventFactory.createArrayBacked(ModifyXp.class, listeners -> (player, skill, xp) -> {
		for (ModifyXp l : listeners) {
			xp = l.modify(player, skill, xp);
			if (xp <= 0) {
				return 0;
			}
		}
		return xp;
	});

	public static final Event<XpGained> XP_GAINED = EventFactory.createArrayBacked(XpGained.class, listeners -> (player, skill, xp) -> {
		for (XpGained l : listeners) {
			l.gained(player, skill, xp);
		}
	});

	public static final Event<SkillLevelUp> SKILL_LEVEL_UP = EventFactory.createArrayBacked(SkillLevelUp.class, listeners -> (player, skill, from, to) -> {
		for (SkillLevelUp l : listeners) {
			l.levelUp(player, skill, from, to);
		}
	});

	public static final Event<CharacterLevelUp> CHARACTER_LEVEL_UP = EventFactory.createArrayBacked(CharacterLevelUp.class, listeners -> (player, from, to) -> {
		for (CharacterLevelUp l : listeners) {
			l.levelUp(player, from, to);
		}
	});

	/** A perk rank bought, or an earned perk (feat, synergy, teaching) granted. */
	public static final Event<PerkLearned> PERK_LEARNED = EventFactory.createArrayBacked(PerkLearned.class, listeners -> (player, perk, rank) -> {
		for (PerkLearned l : listeners) {
			l.learned(player, perk, rank);
		}
	});

	/**
	 * A respec: bought perks refunded (all of them, or one skill's from a Draught of Forgetting); {@code attributes} when
	 * the placed attribute points were returned (a Draught of Unbinding returns only those).
	 */
	public static final Event<Respec> RESPEC = EventFactory.createArrayBacked(Respec.class, listeners -> (player, attributes) -> {
		for (Respec l : listeners) {
			l.respec(player, attributes);
		}
	});

	/** A player chose their background in the picker (not on commands that force one). The kit is already given. */
	public static final Event<BackgroundChosen> BACKGROUND_CHOSEN = EventFactory.createArrayBacked(BackgroundChosen.class, listeners -> (player, background) -> {
		for (BackgroundChosen l : listeners) {
			l.chosen(player, background);
		}
	});

	/** The player's load band changed (once per real change). */
	public static final Event<LoadBandChanged> LOAD_BAND_CHANGED = EventFactory.createArrayBacked(LoadBandChanged.class, listeners -> (player, from, to) -> {
		for (LoadBandChanged l : listeners) {
			l.changed(player, from, to);
		}
	});

	/**
	 * A first visit: a biome, or a structure instance (a landmark when it is in {@code #mettle:landmarks}, or Expanse's
	 * {@code #expanse:landmarks}). {@code id} is the biome or structure id. Fired by Survival's discovery, every 2 s.
	 */
	public static final Event<Discovered> DISCOVERED = EventFactory.createArrayBacked(Discovered.class, listeners -> (player, kind, id, pos) -> {
		for (Discovered l : listeners) {
			l.discovered(player, kind, id, pos);
		}
	});

	// ---------------------------------------------------------------- cards (docs/CARDS.md §25; read them with CardService)

	/**
	 * A hand was dealt for a skill's draft (a level, the next draft after a keep, a reroll, a hand dealt again): the
	 * offers, each with id 0 and place {@code HAND}. Offers never enter the world until kept.
	 */
	public static final Event<CardDrawn> CARD_DRAWN = EventFactory.createArrayBacked(CardDrawn.class, listeners -> (player, skill, hand) -> {
		for (CardDrawn l : listeners) {
			l.drawn(player, skill, hand);
		}
	});

	/** An offer was kept from a draft: it is a card now, in the binder or (Keep &amp; slot) a deck. Mythics: {@link #MYTHIC_CLAIMED}. */
	public static final Event<CardKept> CARD_KEPT = EventFactory.createArrayBacked(CardKept.class, listeners -> (player, card) -> {
		for (CardKept l : listeners) {
			l.kept(player, card);
		}
	});

	/** A card was put in a deck slot (its deck and slot are on the card). */
	public static final Event<CardSlotted> CARD_SLOTTED = EventFactory.createArrayBacked(CardSlotted.class, listeners -> (player, card) -> {
		for (CardSlotted l : listeners) {
			l.slotted(player, card);
		}
	});

	/**
	 * A card came out of {@code deck} back to the binder: a free swap, a rebuild (the card as it came out, scar
	 * included), or an op's command or a respec ({@code paid} false for every free way).
	 */
	public static final Event<CardUnslotted> CARD_UNSLOTTED = EventFactory.createArrayBacked(CardUnslotted.class, listeners -> (player, card, deck, paid) -> {
		for (CardUnslotted l : listeners) {
			l.unslotted(player, card, deck, paid);
		}
	});

	/** Three copies were fused into one card of the next rarity; {@code consumed} are the three (now released). */
	public static final Event<CardFused> CARD_FUSED = EventFactory.createArrayBacked(CardFused.class, listeners -> (player, result, consumed) -> {
		for (CardFused l : listeners) {
			l.fused(player, result, consumed);
		}
	});

	/** A card changed hands in a face-to-face trade: once per card, after the swap ({@code card} as {@code to} holds it). */
	public static final Event<CardTraded> CARD_TRADED = EventFactory.createArrayBacked(CardTraded.class, listeners -> (from, to, card) -> {
		for (CardTraded l : listeners) {
			l.traded(from, to, card);
		}
	});

	/** A player earned a Mythic: a world first, a deed, a drawn one claimed from a hand, or an op's grant. */
	public static final Event<MythicClaimed> MYTHIC_CLAIMED = EventFactory.createArrayBacked(MythicClaimed.class, listeners -> (player, mythic, card) -> {
		for (MythicClaimed l : listeners) {
			l.claimed(player, mythic, card);
		}
	});

	/**
	 * An active set's tier changed: {@code from} and {@code to} are 0 (not active), 2, 4 or 6. Fired when the player's deck,
	 * pins, levels or attributes change what the deck makes active (once per real change).
	 */
	public static final Event<SetTierChanged> SET_TIER_CHANGED = EventFactory.createArrayBacked(SetTierChanged.class, listeners -> (player, keyword, from, to) -> {
		for (SetTierChanged l : listeners) {
			l.changed(player, keyword, from, to);
		}
	});

	private MettleEvents() {
	}

	@FunctionalInterface
	public interface CardDrawn {
		void drawn(ServerPlayer player, ResourceKey<Skill> skill, List<CardView> hand);
	}

	@FunctionalInterface
	public interface CardKept {
		void kept(ServerPlayer player, CardView card);
	}

	@FunctionalInterface
	public interface CardSlotted {
		void slotted(ServerPlayer player, CardView card);
	}

	@FunctionalInterface
	public interface CardUnslotted {
		void unslotted(ServerPlayer player, CardView card, ResourceKey<Skill> deck, boolean paid);
	}

	@FunctionalInterface
	public interface CardFused {
		void fused(ServerPlayer player, CardView result, List<CardView> consumed);
	}

	@FunctionalInterface
	public interface CardTraded {
		void traded(ServerPlayer from, ServerPlayer to, CardView card);
	}

	@FunctionalInterface
	public interface MythicClaimed {
		void claimed(ServerPlayer player, Identifier mythic, CardView card);
	}

	@FunctionalInterface
	public interface SetTierChanged {
		void changed(ServerPlayer player, Identifier keyword, int from, int to);
	}

	public enum DiscoveryKind {
		BIOME, STRUCTURE, LANDMARK
	}

	@FunctionalInterface
	public interface Discovered {
		void discovered(ServerPlayer player, DiscoveryKind kind, Identifier id, BlockPos pos);
	}

	@FunctionalInterface
	public interface ModifyXp {
		double modify(ServerPlayer player, ResourceKey<Skill> skill, double xp);
	}

	@FunctionalInterface
	public interface XpGained {
		void gained(ServerPlayer player, ResourceKey<Skill> skill, double xp);
	}

	@FunctionalInterface
	public interface SkillLevelUp {
		void levelUp(ServerPlayer player, ResourceKey<Skill> skill, int from, int to);
	}

	@FunctionalInterface
	public interface CharacterLevelUp {
		void levelUp(ServerPlayer player, int from, int to);
	}

	@FunctionalInterface
	public interface PerkLearned {
		void learned(ServerPlayer player, ResourceKey<Perk> perk, int rank);
	}

	@FunctionalInterface
	public interface Respec {
		void respec(ServerPlayer player, boolean attributes);
	}

	@FunctionalInterface
	public interface BackgroundChosen {
		void chosen(ServerPlayer player, ResourceKey<Background> background);
	}

	@FunctionalInterface
	public interface LoadBandChanged {
		void changed(ServerPlayer player, LoadBand from, LoadBand to);
	}
}
