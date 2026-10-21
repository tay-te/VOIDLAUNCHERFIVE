package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.combat.Weapons;
import dev.voidmc.fray.combat.action.WeaponFamily;
import dev.voidmc.fray.combat.net.CombatPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lock-on (COMBAT.md §11): middle mouse locks the creature nearest the crosshair within 35°, in sight, within 20 blocks
 * (48 with a bow); scroll switches; breaks on death, beyond 30 (60) blocks or 40 ticks out of sight (the server
 * checks the same). The camera eases toward the target (yaw 20 %, pitch 10 % a frame at 60 fps); against players
 * only the marker shows.
 */
public final class LockOn {
	public static int target = -1;
	public static boolean confirmed;
	public static int lostId = -1;
	public static long lostAt;
	/** Switching: where the reticle slides from, and when. */
	public static int previous = -1;
	public static long switchedAt;
	private static long lastFrame;

	private LockOn() {
	}

	public static void clear() {
		target = -1;
		confirmed = false;
		lostId = -1;
	}

	public static void lost() {
		lostId = target;
		lostAt = ClientCombat.ms();
		target = -1;
		confirmed = false;
	}

	private static boolean ranged(LocalPlayer p) {
		return Weapons.family(p.getMainHandItem()) == WeaponFamily.BOW;
	}

	public static void toggle(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (target >= 0) {
			send(-1);
			lost();
			return;
		}
		boolean fight = ClientCombat.self != null && (ClientCombat.self.flags() & CombatPayloads.SELF_IN_COMBAT) != 0;
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && !fight) {
			// Out of a fight, middle mouse on a block stays vanilla's pick block.
			return;
		}
		LivingEntity e = pick(mc, p, -1, 0);
		if (e != null) {
			target = e.getId();
			confirmed = false;
			switchedAt = ClientCombat.ms();
			previous = -1;
			send(target);
		}
	}

	private static void send(int id) {
		CombatInput.send(CombatPayloads.LOCK, id, 0);
	}

	/** The candidate nearest the crosshair ({@code side} 0), or the nearest to the left (-1) or right (+1) of {@code from}. */
	static LivingEntity pick(Minecraft mc, LocalPlayer p, int from, int side) {
		double range = ranged(p) ? 48 : 20;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1);
		double fromYaw = Double.NaN;
		if (side != 0 && mc.level.getEntity(from) instanceof LivingEntity f) {
			fromYaw = yawTo(eye, f);
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isInvisible() || e instanceof ArmorStand || e.getId() == from) {
				continue;
			}
			Vec3 c = le.getBoundingBox().getCenter();
			Vec3 d = c.subtract(eye);
			double dist = d.length();
			if (dist > range || dist < 0.5) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(Mth.clamp(d.normalize().dot(look), -1, 1)));
			if (side == 0 && angle > 35 || side != 0 && angle > 70) {
				continue;
			}
			if (!p.hasLineOfSight(le)) {
				continue;
			}
			double score = angle + dist * 0.5;
			if (side != 0) {
				double dy = Mth.wrapDegrees(yawTo(eye, le) - fromYaw);
				if (Math.signum(dy) != side) {
					continue;
				}
				score = Math.abs(dy);
			}
			if (score < bestScore) {
				bestScore = score;
				best = le;
			}
		}
		return best;
	}

	private static double yawTo(Vec3 eye, Entity e) {
		Vec3 d = e.position().subtract(eye);
		return Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90;
	}

	/** Scroll while locked switches target (and doesn't change the hotbar). */
	public static boolean scroll(double dy) {
		Minecraft mc = Minecraft.getInstance();
		if (target < 0 || mc.player == null || dy == 0) {
			return false;
		}
		LivingEntity next = pick(mc, mc.player, target, dy > 0 ? -1 : 1);
		if (next != null) {
			previous = target;
			target = next.getId();
			switchedAt = ClientCombat.ms();
			confirmed = false;
			send(target);
		}
		return true;
	}

	public static void tick(Minecraft mc) {
		if (target < 0 || mc.player == null) {
			return;
		}
		Entity e = mc.level.getEntity(target);
		double max = ranged(mc.player) ? 60 : 30;
		if (!(e instanceof LivingEntity le) || !le.isAlive() || e.distanceTo(mc.player) > max) {
			lost();
		}
	}

	/** Called every frame after the mouse turned the player: the soft follow. */
	public static void frame(Minecraft mc) {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 1 / 60.0 : Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		LocalPlayer p = mc.player;
		if (target < 0 || p == null || mc.level == null || mc.gui.screen() != null || !FrayClientOptions.get().lockOnEase) {
			return;
		}
		Entity e = mc.level.getEntity(target);
		if (!(e instanceof LivingEntity le) || e instanceof Player) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 c = le.position().add(0, le.getBbHeight() * 0.6, 0);
		Vec3 d = c.subtract(eye);
		float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
		float wantPitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		float fy = (float) (1 - Math.pow(0.8, dt * 60));
		float fp = (float) (1 - Math.pow(0.9, dt * 60));
		float dyaw = Mth.wrapDegrees(wantYaw - p.getYRot()) * fy;
		float dpitch = (wantPitch - p.getXRot()) * fp;
		p.turn(dyaw / 0.15, dpitch / 0.15);
	}
}
