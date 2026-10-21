package dev.voidmc.hearth.api;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's skills, attributes and perks, as a character mod (VOID Mettle) keeps them: the numbers Hearth's checks read.
 * Hearth owns the checks themselves (speech, haggling, lockpicking, pickpocketing, stealth) and how the world reacts;
 * the skills mod owns the levels and decides what XP is worth. Keys are plain strings so Hearth needs none of its types.
 *
 * <p>Registered in {@link HearthServices#setPlayerSkills}; without a skills mod {@link #NONE} answers, and every Hearth
 * check then has exactly the odds it had before this slot existed (a level of 0 and an attribute of 3 add nothing).
 * Server thread only.
 *
 * <p>Where Hearth reads it (docs/ARCHITECTURE.md §11, Partners):
 * <pre>
 *   speech checks (persuade, intimidate, bribe, flatter)  {@link #SPEECH} level and {@link #PRESENCE}
 *   haggling (the merchant's room and patience)            {@link #SPEECH}
 *   lockpicking and pickpocketing                          {@link #THIEVERY} (with Hearth's own practice; the better counts)
 *   being seen and heard (witnesses, the mark's awareness) {@link #STEALTH}
 * </pre>
 * and where it calls {@link #award}: a passed speech check, a struck haggle, a picked lock, a lifted pocket, a crime
 * nobody saw. Reasons are namespaced ({@code hearth:speech/persuade}, {@code hearth:law/lockpick}...).
 */
public interface PlayerSkills {
	String SPEECH = "mettle:speech";
	String THIEVERY = "mettle:thievery";
	String STEALTH = "mettle:stealth";

	String PRESENCE = "presence";
	String AGILITY = "agility";
	String WITS = "wits";
	String STRENGTH = "strength";
	String VITALITY = "vitality";

	// ---- named bonuses at Hearth's decision points (docs/requests/mettle-speech-stealth.md §12, added 2026-10-21)

	/** Speech levels added only for reading a merchant's limit band (Hearth clamps to 0–10). */
	String BONUS_HAGGLE_BAND = "hearth:haggle/band";
	/** Share added to a merchant's patience at the haggle (Hearth clamps to 0–0.5). */
	String BONUS_HAGGLE_PATIENCE = "hearth:haggle/patience";
	/** 1: a merchant walking away from a haggle is never sour with the player. */
	String BONUS_HAGGLE_NEVER_SOUR = "hearth:haggle/never_sour";
	/** Odds added to a speech check of one kind before Hearth's clamp: append the kind ({@code persuade}, {@code intimidate}, {@code bribe}, {@code flatter}). */
	String BONUS_SPEECH = "hearth:speech/";
	/** Failed speech checks per sim day that may be tried again at once (Hearth clamps to 0–3). */
	String BONUS_SPEECH_RETRY = "hearth:speech/retry";
	/** Share added to positive standing changes only (Hearth clamps to 0–0.5). */
	String BONUS_STANDING_GAIN = "hearth:standing/gain";
	/** Share off how far a sneaking player is seen, on top of Stealth's (Hearth clamps to 0–0.5). */
	String BONUS_STEALTH_SIGHT = "hearth:stealth/sight";
	/** Blocks beyond which a sneaking player is not seen at all; 0 = off (Hearth clamps to 2–32 when on). */
	String BONUS_STEALTH_UNSEEN_BEYOND = "hearth:stealth/unseen_beyond";
	/** Share off how long a guard searches after losing the player (Hearth clamps to 0–0.75). */
	String BONUS_STEALTH_GIVE_UP = "hearth:stealth/give_up";
	/** Share off how fast a pickpocketing mark notices (Hearth clamps to 0–0.5). */
	String BONUS_PICKPOCKET_AWARENESS = "hearth:pickpocket/awareness";
	/** 1: folk greet the player as a hero; flavour only, standing and the per-town HERO title untouched. */
	String BONUS_GREET_HERO = "hearth:greet/hero";

	/** A skill's level when the skills mod does not know it (or is absent). Skills run 0–20. */
	int UNKNOWN_LEVEL = 0;
	/** An attribute's value when unknown: the starting value. Attributes run 1–20. */
	int UNKNOWN_ATTRIBUTE = 3;

	/** The player's level in a skill ({@code mettle:speech}...), 0–20; 0 when unknown. */
	int level(ServerPlayer player, String skill);

	/** The player's attribute ({@code presence}...), 1–20; 3 when unknown. */
	int attribute(ServerPlayer player, String attribute);

	boolean hasPerk(ServerPlayer player, Identifier perk);

	/**
	 * A plain skill check, rolled now: for content that has no Hearth model of its own.
	 *
	 * @param difficulty 1–25 by convention: 5 easy, 10 fair, 15 hard, 20 heroic
	 */
	Check check(ServerPlayer player, String skill, int difficulty);

	/** Hearth reports something worth XP in a skill (a lock picked, a check passed); a no-op without a skills mod. */
	void award(ServerPlayer player, String skill, float xp, String reason);

	/**
	 * A character mod's bonus at one of Hearth's decision points ({@code BONUS_*} keys above); 0 when unknown or without
	 * a skills mod. Hearth applies and clamps it where it decides (docs/ARCHITECTURE.md §11); a perk answers either here
	 * or through {@link #level}, never both. Added 2026-10-21.
	 */
	default double bonus(ServerPlayer player, String key) {
		return 0;
	}

	/** Mythic cards the player holds ({@code mettle:mythic/dragonfall}); empty when unknown. Folk may greet them by it. */
	default List<Identifier> titles(ServerPlayer player) {
		return List.of();
	}

	/** The title the player wears ("the Deep Ember Warden", "Long Walker"); empty when none. Folk may greet them by it. */
	default Component title(ServerPlayer player) {
		return Component.empty();
	}

	/**
	 * A bonus as Hearth applies it: {@link #bonus} within {@code lo}–{@code hi}, and 0 for a missing player, NaN or a
	 * skills mod that throws (a broken partner never breaks a Hearth check).
	 */
	static double bonus(PlayerSkills skills, @org.jspecify.annotations.Nullable ServerPlayer player, String key, double lo, double hi) {
		double v;
		try {
			v = skills.bonus(player, key);
		} catch (RuntimeException e) {
			return 0;
		}
		if (Double.isNaN(v) || v == 0) {
			return 0;
		}
		return Math.clamp(v, lo, hi);
	}

	/**
	 * @param odds the chance it had, 0–1
	 * @param roll the roll, 0–1 (passed when below the odds)
	 */
	record Check(double odds, double roll) {
		public boolean passed() {
			return this.roll < this.odds;
		}

		public int percent() {
			return (int) Math.round(this.odds * 100);
		}
	}

	/**
	 * The odds of a plain check: 50 % + 5 % × (level + attribute ÷ 2 − difficulty), within 5–95 % (the skills mod's own
	 * rule, which {@link #NONE} follows with neutral numbers).
	 */
	static double odds(int level, int attribute, int difficulty) {
		return Math.clamp(0.5 + 0.05 * (level + attribute / 2.0 - difficulty), 0.05, 0.95);
	}

	/** No skills mod: every skill unknown, every attribute at its start, no perks, no XP. */
	PlayerSkills NONE = new PlayerSkills() {
		@Override
		public int level(ServerPlayer player, String skill) {
			return UNKNOWN_LEVEL;
		}

		@Override
		public int attribute(ServerPlayer player, String attribute) {
			return UNKNOWN_ATTRIBUTE;
		}

		@Override
		public boolean hasPerk(ServerPlayer player, Identifier perk) {
			return false;
		}

		@Override
		public Check check(ServerPlayer player, String skill, int difficulty) {
			return new Check(PlayerSkills.odds(UNKNOWN_LEVEL, UNKNOWN_ATTRIBUTE, difficulty), player.getRandom().nextDouble());
		}

		@Override
		public void award(ServerPlayer player, String skill, float xp, String reason) {
		}

		@Override
		public String toString() {
			return "PlayerSkills.NONE";
		}
	};
}
