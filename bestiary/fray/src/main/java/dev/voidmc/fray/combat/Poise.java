package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.Rank;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Poise sizes (COMBAT.md §9). Creatures: the {@code fray:poise} attribute (Bestiary's profile base; vanilla defaults set
 * at registration) × the rank's scale. Players: the armour class (light 10, medium 18, heavy 28) plus whatever
 * modifies {@code fray:poise} above its default, × (1 + {@code mettle:poise} %).
 */
public final class Poise {
	public static final int PLAYER_REFILL = 40;
	public static final int FLINCH_TICKS = 6;
	public static final float HYPER_ARMOUR = 20;
	private static final float[] CLASS_POISE = {10, 18, 28};

	private Poise() {
	}

	public static float max(LivingEntity e, Rank rank) {
		double attr = e.getAttributes().hasAttribute(FrayAttributes.POISE) ? e.getAttributeValue(FrayAttributes.POISE) : 20;
		if (e instanceof Player p) {
			return (float) ((CLASS_POISE[Weapons.armourClass(p)] + (attr - 20)) * (1 + MettleStats.add(p, "poise") / 100.0));
		}
		return (float) attr * rank.poiseScale;
	}
}
