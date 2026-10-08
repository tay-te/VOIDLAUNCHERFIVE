package dev.voidmc.hearth.law.stealth;

import dev.voidmc.hearth.law.LawSkills;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Reads a player's {@link Detection.Stance} from the world: the light at their eyes, whether they crouch or run, the
 * armour they wear, whether they moved lately. Player movement is the client's, so "moving" comes from where the
 * server last saw them rather than from their velocity.
 */
public final class Visibility {
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Boolean> MOVING = new HashMap<>();

	private Visibility() {
	}

	/** Called every few ticks for players near settlements: notes whether they moved since the last call. */
	public static void sample(ServerPlayer player) {
		Vec3 now = player.position();
		Vec3 before = LAST.put(player.getUUID(), now);
		MOVING.put(player.getUUID(), before != null && before.distanceToSqr(now) > 0.04);
	}

	public static void forget(UUID player) {
		LAST.remove(player);
		MOVING.remove(player);
	}

	public static void clear() {
		LAST.clear();
		MOVING.clear();
	}

	public static Detection.Stance stance(ServerPlayer player) {
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		int light = player.level().getMaxLocalRawBrightness(eyes);
		boolean moving = MOVING.getOrDefault(player.getUUID(), false) || player.isSprinting();
		return new Detection.Stance(light, player.isShiftKeyDown(), player.isSprinting(), moving, player.getArmorValue(), player.isInvisible(),
			LawSkills.stealth(player));
	}
}
