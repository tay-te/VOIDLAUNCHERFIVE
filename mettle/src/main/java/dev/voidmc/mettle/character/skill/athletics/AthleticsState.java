package dev.voidmc.mettle.character.skill.athletics;

import net.minecraft.world.entity.player.Player;

/**
 * Athletics' per-player working state, kept on the player object itself (a field added by {@code mixin.athletics}). The
 * client's own player and the server's player are different objects even in single player, so each side keeps its own
 * stride timer and scramble without a shared map to mix them up, and nothing needs clearing when a player leaves.
 */
public final class AthleticsState {
	/** Ticks of unbroken sprinting (Courier's Stride). */
	public int sprintTicks;
	public boolean stride;
	/** Freerunner: the y the current jump left the ground from, whether a scramble is on, and whether it's spent. */
	public double jumpStartY;
	public boolean scrambling;
	public boolean scrambleSpent;
	public long scrambleChipAt = -1000;
	/** Server: scrambles accepted (the tour reads it). */
	public int scrambles;
	public long haulerChipAt = -1000;
	public long marathonChipAt = -1000;

	// Server only: movement XP waiting to be paid, and the per-minute cap.
	public double pendingXp;
	public final AthleticsRules.Meter meter = new AthleticsRules.Meter();
	public long underwaterCm;

	public static AthleticsState of(Player player) {
		return ((Holder) player).mettle$athletics();
	}

	/** Implemented on Player by the athletics mixin. */
	public interface Holder {
		AthleticsState mettle$athletics();
	}
}
