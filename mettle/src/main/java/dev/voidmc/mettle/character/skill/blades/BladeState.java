package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.skill.Combat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** One player's fight memory for the Blades perks: windows, streaks and cooldowns, in game ticks. Server only, never saved. */
public final class BladeState {
	private static final Map<UUID, BladeState> STATES = new HashMap<>();

	int momentumTarget = -1;
	long momentumAt;
	int momentumStacks;

	long parryUntil = -1;
	long riposteUntil = -1;
	long counterUntil = -1;

	long lastHurt = Long.MIN_VALUE / 2;
	long unbrokenUsed = Long.MIN_VALUE / 2;
	int streak;

	Combat.@Nullable Weapon lastWeapon;
	Combat.@Nullable Weapon weaponMasterArmed;
	long weaponMasterReady;

	long whirlwindReady;
	boolean whirlwindAnnounce;

	/** The first reset of the swing charge before this tick is ignored (the swing packet that follows a kill). */
	long keepChargeUntil = -1;
	long lightFeetChipAt;

	private BladeState() {
	}

	public static BladeState of(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new BladeState());
	}

	public static void forget(UUID player) {
		STATES.remove(player);
	}

	public static void clear() {
		STATES.clear();
	}
}
