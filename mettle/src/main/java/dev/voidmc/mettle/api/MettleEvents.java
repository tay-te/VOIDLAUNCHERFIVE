package dev.voidmc.mettle.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
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

	/** Bought perks refunded; {@code attributes} when the placed attribute points were returned too. */
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

	private MettleEvents() {
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
