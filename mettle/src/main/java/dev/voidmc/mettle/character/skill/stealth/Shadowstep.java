package dev.voidmc.mettle.character.skill.stealth;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shadowstep (a Stealth mastery): a sneak-jump at a mob within 8 blocks that isn't targeting you puts you just behind it,
 * facing its back. Never at a player; walls stop it (the player must see the mob, and the spot behind it must be free).
 */
final class Shadowstep {
	/** How far off the player's look a mob may stand and still be the one aimed at. */
	private static final double AIM = Math.cos(Math.toRadians(20));

	private Shadowstep() {
	}

	/** The unaware mob the player is looking at, nearest first, or null. */
	static @Nullable Mob target(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Mob best = null;
		double bestDist = Double.MAX_VALUE;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(StealthRules.SHADOWSTEP_RANGE),
			m -> m.isAlive() && m.getTarget() != player && m.distanceTo(player) <= StealthRules.SHADOWSTEP_RANGE)) {
			Vec3 to = m.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 1e-3 || to.normalize().dot(look) < AIM || !player.hasLineOfSight(m)) {
				continue;
			}
			if (d < bestDist) {
				bestDist = d;
				best = m;
			}
		}
		return best;
	}

	/** Steps behind the mob when there is room there; false (nothing happens) when there isn't. */
	static boolean step(ServerPlayer player, Mob mob) {
		float yaw = mob.getYRot();
		Vec3 forward = Vec3.directionFromRotation(0, yaw);
		double back = mob.getBbWidth() / 2 + player.getBbWidth() / 2 + 0.4;
		Vec3 dest = new Vec3(mob.getX() - forward.x * back, mob.getY(), mob.getZ() - forward.z * back);
		if (!player.level().noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())))) {
			return false;
		}
		// The way there must be open too: no stepping through a wall to the far side of a mob behind it.
		if (!player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), dest.add(0, player.getEyeHeight(), 0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType()
			.equals(net.minecraft.world.phys.HitResult.Type.MISS)) {
			return false;
		}
		return player.teleportTo(player.level(), dest.x, dest.y, dest.z, Set.of(), yaw, player.getXRot(), false);
	}
}
