package dev.voidmc.hearth.economy.haggle;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.economy.EconomyConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * A merchant's temper at the haggle: how much room they have at heart and how much patience, from the player's charisma
 * and standing (the standing service's, when it runs), the merchant's opinion of the player, and their traits — the
 * greedy give little room but enjoy the game, the generous give more, the grumpy have short fuses and the cheerful long
 * ones. Traits are read by name from the NPC module's part of the resident ({@code extra.npc.traits}), so the economy
 * needs nothing of the NPC module but its data.
 */
public final class Temper {
	private Temper() {
	}

	/** The share of the price a merchant will give at heart, 0..0.6. */
	public static double room(EconomyConfig.Haggle cfg, float charisma, int standing, Set<String> traits) {
		double r = cfg.room() * (1 + cfg.charismaRoom() * clamp(charisma, -1, 1)) * (1 + cfg.standingRoom() * clamp(standing, -100, 100) / 100.0);
		if (traits.contains("greedy")) {
			r *= 0.6;
		}
		if (traits.contains("generous")) {
			r *= 1.4;
		}
		if (traits.contains("cheerful")) {
			r *= 1.1;
		}
		if (traits.contains("grumpy")) {
			r *= 0.85;
		}
		return clamp(r, 0, 0.6);
	}

	/** How much haggling a merchant will bear before walking away. */
	public static double patience(EconomyConfig.Haggle cfg, int opinion, Set<String> traits) {
		double p = cfg.patience() * (1 + clamp(opinion, -100, 100) / 200.0);
		if (traits.contains("grumpy")) {
			p *= 0.7;
		}
		if (traits.contains("cheerful")) {
			p *= 1.2;
		}
		if (traits.contains("sociable")) {
			p *= 1.1;
		}
		if (traits.contains("shy")) {
			p *= 0.9;
		}
		if (traits.contains("greedy")) {
			p *= 1.15; // they enjoy the game
		}
		return Math.max(1, p);
	}

	/**
	 * The room a merchant gives a skilled talker (a character mod's Speech, 0–20): 2 % more per level, still within
	 * 0..0.6. Level 0 (no skills mod) leaves it as it was.
	 */
	public static double skilled(double room, int speech) {
		if (speech <= 0) {
			return room;
		}
		return clamp(room * (1 + 0.02 * Math.min(20, speech)), 0, 0.6);
	}

	/** The factor on a merchant's patience for a skilled talker: 1.5 % more per level of Speech (1 at level 0). */
	public static double skilledPatience(int speech) {
		return 1 + 0.015 * Math.clamp(speech, 0, 20);
	}

	// ---- a character mod's named bonuses (PlayerSkills.bonus, docs/requests/mettle-speech-stealth.md §12)

	/** The most patience a bonus may add, as a share. */
	public static final double MAX_PATIENCE_BONUS = 0.5;
	/** The most Speech levels a bonus may add for reading the limit band. */
	public static final int MAX_BAND_BONUS = 10;

	/** A merchant's patience with a bonus share ({@code hearth:haggle/patience}) added: within 0–{@link #MAX_PATIENCE_BONUS}. */
	public static double patience(double patience, double bonusShare) {
		return patience * (1 + Math.clamp(Double.isNaN(bonusShare) ? 0 : bonusShare, 0, MAX_PATIENCE_BONUS));
	}

	/** The Speech a talker reads the limit band with: their level plus the {@code hearth:haggle/band} levels (0–{@link #MAX_BAND_BONUS}), at most 20. */
	public static int bandSpeech(int speech, double bonusLevels) {
		int add = (int) Math.floor(Math.clamp(Double.isNaN(bonusLevels) ? 0 : bonusLevels, 0, MAX_BAND_BONUS));
		return Math.min(20, Math.max(0, speech) + add);
	}

	/** Whether a merchant walking away stays civil ({@code hearth:haggle/never_sour} at 1, read as at least a half). */
	public static boolean neverSour(double bonus) {
		return bonus >= 0.5;
	}

	/**
	 * Speech XP for a struck haggle: nothing for paying the asking price, else 5 plus one per two bits saved (or gained)
	 * against it, at most 40.
	 */
	public static float haggleXp(long list, long agreed) {
		long won = Math.abs(list - agreed);
		if (won <= 0) {
			return 0;
		}
		return (float) Math.min(40.0, 5.0 + won / 2.0);
	}

	/** A resident's traits by name ({@code extra.npc.traits}); empty without the NPC module's data. */
	public static Set<String> traits(@Nullable Resident resident) {
		Set<String> out = new HashSet<>();
		if (resident == null) {
			return out;
		}
		CompoundTag npc = resident.extra.getCompoundOrEmpty("npc");
		ListTag list = npc.getListOrEmpty("traits");
		for (Tag t : list) {
			t.asString().ifPresent(out::add);
		}
		return out;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}
}
