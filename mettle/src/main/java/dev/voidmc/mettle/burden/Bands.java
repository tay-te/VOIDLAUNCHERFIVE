package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.api.BandModifier;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import dev.voidmc.mettle.api.MettleServices;
import net.minecraft.world.entity.player.Player;

/**
 * Load bands (BURDEN.md §3, BALANCE.md §5.1) and the size of each penalty. Pure numbers apart from {@link #rules}, so the
 * tests cover them directly.
 */
public final class Bands {
	/** movement_speed, ADD_MULTIPLIED_TOTAL, per band (before the penalty scale). */
	public static final float[] SPEED = {0f, -0.10f, -0.30f, -0.70f};
	/** jump_strength, ADD_MULTIPLIED_TOTAL. */
	public static final float[] JUMP = {0f, 0f, -0.40f, -1.0f};
	/** fall_damage_multiplier, ADD_MULTIPLIED_TOTAL. */
	public static final float[] FALL = {0f, 0.25f, 0.25f, 0.25f};
	/** Extra hunger drain: ×1.5 from Burdened on. */
	public static final float[] HUNGER = {0f, 0.5f, 0.5f, 0.5f};
	/** A mount's movement_speed while an Overburdened or Pinned rider steers it. */
	public static final float[] MOUNT = {0f, 0f, -0.20f, -0.40f};
	/** Downward pull per tick in water (blocks/tick²); swimming up still works, just slowly. */
	public static final float[] SINK = {0f, 0f, 0.012f, 0.02f};

	private Bands() {
	}

	/** The player's thresholds after every registered {@link BandModifier}. Both sides. */
	public static BandRules rules(Player player) {
		BandRules rules = BandRules.DEFAULT;
		for (BandModifier modifier : MettleServices.bandModifiers()) {
			rules = modifier.adjust(player, rules);
		}
		return rules;
	}

	/** Light under 75 %, Burdened from 75 %, Overburdened from 100 %, Pinned over 125 % (unless the rules forbid it). */
	public static LoadBand band(float load, float capacity, BandRules rules) {
		if (load <= 0f) {
			return LoadBand.LIGHT;
		}
		float ratio = capacity <= 0f ? Float.POSITIVE_INFINITY : load / capacity;
		LoadBand band;
		if (ratio > rules.pinned()) {
			band = LoadBand.PINNED;
		} else if (ratio >= rules.overburdened()) {
			band = LoadBand.OVERBURDENED;
		} else if (ratio >= rules.burdened()) {
			band = LoadBand.BURDENED;
		} else {
			band = LoadBand.LIGHT;
		}
		return band == LoadBand.PINNED && !rules.canBePinned() ? LoadBand.OVERBURDENED : band;
	}

	/** A penalty for a band, scaled (Ox halves them). */
	public static float scaled(float[] table, LoadBand band, BandRules rules) {
		return table[band.ordinal()] * Math.max(0f, rules.penaltyScale());
	}

	public static boolean atLeast(LoadBand band, LoadBand floor) {
		return band.ordinal() >= floor.ordinal();
	}
}
