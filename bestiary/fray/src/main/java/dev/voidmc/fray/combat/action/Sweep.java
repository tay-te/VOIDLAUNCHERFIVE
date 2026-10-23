package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.combat.DamageSplit;
import dev.voidmc.fray.combat.Weapons;
import dev.voidmc.fray.combat.math.DamageMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Player melee hit detection (COMBAT.md §10.1): each ACTIVE tick the server tests the move's arc (angle, reach,
 * height band) from the eye along the look taken at the first ACTIVE tick; line of sight to the target; each target
 * once per action. The declared target (crosshair or lock) is tested first and accepted within reach + 0.5 (lag
 * margin).
 */
public final class Sweep {
	/** Cost probes for the perf test. */
	public static long sweepNanos;
	public static long sweeps;

	private Sweep() {
	}

	/** The swing's direction: the look, snapped to the lock target if it is within 60° (PvE only). */
	static float aimYaw(PlayerCombat pc, PlayerCombat.Run r) {
		ServerPlayer p = pc.player;
		float yaw = p.getYRot();
		LivingEntity lock = pc.lockEntity();
		if (lock != null && !(lock instanceof Player)) {
			Vec3 d = lock.position().subtract(p.position());
			float to = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
			if (Math.abs(Mth.wrapDegrees(to - yaw)) <= 60) {
				yaw = to;
			}
		}
		return yaw;
	}

	static void sweep(PlayerCombat pc, PlayerCombat.Run r, int k, ServerLevel level) {
		long t0 = System.nanoTime();
		ServerPlayer p = pc.player;
		Vec3 eye = p.getEyePosition();
		float reach = r.def.reach() + (float) Math.max(0, p.entityInteractionRange() - 3.0);
		double rad = Math.toRadians(r.yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad);
		AABB box = new AABB(eye, eye).inflate(reach + 1.5);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator() && e.isAttackable()
			&& !r.hit.contains(e.getId()) && !p.isAlliedTo(e) && !(e instanceof OwnableEntity o && o.getOwner() == p) && !(e instanceof ArmorStand a && a.isMarker()));
		List<LivingEntity> order = new ArrayList<>(near.size());
		for (LivingEntity e : near) {
			if (e.getId() == r.declared) {
				order.addFirst(e);
			} else {
				order.add(e);
			}
		}
		for (LivingEntity e : order) {
			AABB bb = e.getBoundingBox();
			boolean declared = e.getId() == r.declared;
			double cx = Mth.clamp(eye.x, bb.minX, bb.maxX), cy = Mth.clamp(eye.y, bb.minY, bb.maxY), cz = Mth.clamp(eye.z, bb.minZ, bb.maxZ);
			double dist = Math.sqrt((cx - eye.x) * (cx - eye.x) + (cy - eye.y) * (cy - eye.y) + (cz - eye.z) * (cz - eye.z));
			if (dist > reach + (declared ? 0.5 : 0)) {
				continue;
			}
			if (bb.maxY < eye.y - 2.4 || bb.minY > eye.y + 1.4) {
				continue;
			}
			if (r.def.arc() < 360) {
				Vec3 c = bb.getCenter();
				double dx = c.x - eye.x, dz = c.z - eye.z;
				double h = Math.sqrt(dx * dx + dz * dz);
				if (h > 0.6) {
					double cos = (dx * fx + dz * fz) / h;
					double angle = Math.toDegrees(Math.acos(Mth.clamp(cos, -1, 1)));
					if (angle > r.def.arc() / 2 + (declared ? 15 : 0)) {
						continue;
					}
				}
			}
			if (!p.hasLineOfSight(e)) {
				continue;
			}
			r.hit.add(e.getId());
			hit(pc, r, e);
		}
		sweepNanos += System.nanoTime() - t0;
		sweeps++;
	}

	static void hit(PlayerCombat pc, PlayerCombat.Run r, LivingEntity target) {
		ServerPlayer p = pc.player;
		ItemStack weapon = p.getMainHandItem();
		WeaponFamily fam = Weapons.family(weapon);
		float base = fam == WeaponFamily.BOW ? 2 : (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float mult = r.def.multiplier();
		if (r.kind == ActionKind.RUNNING) {
			mult *= DamageMath.RUNNING;
		}
		if (r.slipBonus) {
			mult *= Movesets.SLIP_BONUS;
		}
		DamageSplit split = Weapons.split(weapon);
		float situational = 1;
		int flags = r.kind.flags;
		if (r.kind == ActionKind.CHARGED) {
			situational = DamageMath.charged(r.charge, Movesets.of(fam).chargeMax());
		}
		float poiseFactor = split.poiseFactor() * (r.kind == ActionKind.EARTHSHAKER ? 3 : 1);
		if (weakPoint(p.getEyePosition(), p.getLookAngle(), r.def.reach() + 1.5F, target)) {
			flags |= HitFlags.WEAK_POINT;
		}
		DamageSpec spec = FrayApi.damage().specFor(weapon, base * mult, 1);
		HitContext ctx = new HitContext(p, weapon, r.kind.id(), spec, flags | (r.def.arc() >= 360 ? HitFlags.AOE : 0), r.charge, null, situational, poiseFactor);
		FrayApi.damage().deal(target, ctx);
	}

	/**
	 * Whether a swing along {@code look} from {@code eye} reaches one of the target's exposed weak points (a hurt volume of
	 * its running clip, ANIM_EVENTS.md §2: the Gallows Knight's chest while it hauls its chain): the swing's line comes
	 * within the capsule's radius plus a 0.6-block blade margin.
	 *
	 * @since 0.3.0 (2026-10-22)
	 */
	public static boolean weakPoint(Vec3 eye, Vec3 look, float reach, LivingEntity target) {
		List<dev.voidmc.fray.motion.MotionServer.Placed> hv = dev.voidmc.fray.motion.MotionServer.hurtVolumesNow(target);
		if (hv.isEmpty()) {
			return false;
		}
		Vec3 end = eye.add(look.scale(reach));
		for (dev.voidmc.fray.motion.MotionServer.Placed v : hv) {
			if (dev.voidmc.fray.combat.math.DamageMath.segmentDistance(eye, end, v.a(), v.b()) <= v.radius() + 0.6) {
				return true;
			}
		}
		return false;
	}
}
