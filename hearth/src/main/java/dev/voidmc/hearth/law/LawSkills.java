package dev.voidmc.hearth.law;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import net.minecraft.server.level.ServerPlayer;

/**
 * The law's reading of a character's skills ({@link PlayerSkills}, a character mod's; neutral without one) and the XP it
 * reports back. The law keeps its own thief's practice ({@link PlayerLaw}); against a character mod's Thievery the
 * better of the two counts, so without one every minigame plays exactly as before. Server thread only.
 */
public final class LawSkills {
	private LawSkills() {
	}

	/** Lockpicking skill, 0–20: the law's practice or the character's Thievery, whichever is better. */
	public static int lockpick(ServerPlayer player) {
		return better(PlayerLaw.of(player).lockpickSkill(), level(player, PlayerSkills.THIEVERY));
	}

	/** Pickpocketing skill, 0–20, as {@link #lockpick}. */
	public static int pickpocket(ServerPlayer player) {
		return better(PlayerLaw.of(player).pickpocketSkill(), level(player, PlayerSkills.THIEVERY));
	}

	/** Stealth, 0–20 (a character mod's; 0 without one): shortens how far folk see and hear a sneaking player. */
	public static int stealth(ServerPlayer player) {
		return level(player, PlayerSkills.STEALTH);
	}

	/** The pure rule: the better of the law's practice skill and a character's level, within 0–20. */
	public static int better(int practice, int level) {
		return Math.clamp(Math.max(practice, level), 0, 20);
	}

	/**
	 * A law speech check's odds (persuading a guard, bribing or threatening a witness) with the character's Speech and
	 * Presence added: +1 % per level of Speech and +1 % per point of Presence above 3, kept within the law's 5–92 %.
	 * Speech 0 and Presence 3 leave the odds untouched.
	 */
	public static double speech(ServerPlayer player, double odds) {
		PlayerSkills skills = HearthServices.playerSkills();
		return speech(odds, skills.level(player, PlayerSkills.SPEECH), skills.attribute(player, PlayerSkills.PRESENCE));
	}

	/** The pure rule of {@link #speech(ServerPlayer, double)}. */
	public static double speech(double odds, int speech, int presence) {
		double bonus = 0.01 * Math.clamp(speech, 0, 20) + 0.01 * (Math.clamp(presence, 1, 20) - 3);
		if (bonus == 0) {
			return odds;
		}
		return Math.clamp(odds + bonus, 0.05, 0.92);
	}

	/** Reports XP to the character mod (a no-op without one). {@code reason} under {@code hearth:law/}. */
	public static void award(ServerPlayer player, String skill, float xp, String reason) {
		if (xp > 0) {
			HearthServices.playerSkills().award(player, skill, xp, "hearth:law/" + reason);
		}
	}

	/** Speech XP for a law check passed (a guard talked round, a witness hushed): 20, or 7 for a failed one. */
	public static void speechCheck(ServerPlayer player, boolean passed, String what) {
		award(player, PlayerSkills.SPEECH, passed ? 20.0F : 7.0F, what);
	}

	/** Thievery XP for a picked lock: 10 a tier (a very hard lock is worth 50). */
	public static float lockXp(int tier) {
		return 10.0F * Math.clamp(tier, 1, 5);
	}

	/** Thievery XP for something lifted from a pocket: 6, plus one per 5 bits it is worth, at most 30. */
	public static float pocketXp(long value) {
		return (float) Math.min(30.0, 6.0 + Math.max(0, value) / 5.0);
	}

	/** Stealth XP for a crime nobody saw: 5, plus one per 10 bits of what was taken, at most 25. */
	public static float unseenXp(long value) {
		return (float) Math.min(25.0, 5.0 + Math.max(0, value) / 10.0);
	}

	private static int level(ServerPlayer player, String skill) {
		return HearthServices.playerSkills().level(player, skill);
	}
}
