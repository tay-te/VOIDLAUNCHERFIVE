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
