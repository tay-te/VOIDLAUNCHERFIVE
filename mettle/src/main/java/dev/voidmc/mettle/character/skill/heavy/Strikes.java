package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.HeavyWeaponsSkill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What a heavy blow does once it lands (TREES.md §5): Cleave, Bone Breaker, Stagger, Overhead Blow's daze, Earthshaker
 * and Berserker's lifesteal. Run from the end of {@code Player#attack}, after vanilla has dealt the hit.
 */
public final class Strikes {
	/** Set while Cleave spills damage, so the spill neither cleaves again nor gets Heft or Berserker on top. */
	private static boolean splashing;
	private static final Map<UUID, Long> EARTHSHAKER_READY = new HashMap<>();

	private Strikes() {
	}

	public static boolean splashing() {
		return splashing;
	}

	public static void afterAttack(HeavyHit hit) {
		if (!hit.hurt || !hit.heavy() || !(hit.target instanceof LivingEntity target) || !(hit.player.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer player = hit.player;
		lifesteal(player, hit.healthBefore - Math.max(0f, target.getHealth()));
		if (hit.axe && hit.damage > 0 && Perks.has(player, HeavyWeaponsSkill.CLEAVE)) {
			cleave(level, player, target, hit.damage * HeavyRules.CLEAVE_SHARE);
		}
		if (hit.crit && target.isAlive() && !(target instanceof Player p && Stance.raging(p)) && Perks.has(player, HeavyWeaponsSkill.BONE_BREAKER)) {
			Stagger.slow(target, HeavyRules.BONE_BREAKER_TICKS);
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.BONE_BREAKER);
		}
		if (hit.fullStrength && target instanceof Mob mob && mob.isAlive() && Perks.has(player, HeavyWeaponsSkill.STAGGER)) {
			Stagger.missNext(mob);
			Stagger.stagger(mob, HeavyRules.STAGGER_TICKS);
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.STAGGER);
		}
		if (hit.overhead) {
			Stagger.stagger(target, HeavyRules.OVERHEAD_STAGGER_TICKS);
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.OVERHEAD_BLOW);
		}
		if ((hit.smash || player.isShiftKeyDown() && hit.fullStrength) && Perks.has(player, HeavyWeaponsSkill.EARTHSHAKER)) {
			earthshaker(level, player);
		}
	}

	/** Berserker: a heavy hit in a rage heals a fifth of the damage it dealt. */
	public static void lifesteal(ServerPlayer player, float dealt) {
		if (dealt > 0 && Stance.raging(player)) {
			player.heal(dealt * HeavyRules.BERSERK_LIFESTEAL);
		}
	}

	/** Whether a heavy blow may spill onto this creature: hostiles and whatever is hunting you, never pets or folk. */
	public static boolean isFoe(ServerPlayer player, LivingEntity other) {
		if (other == player || !other.isAlive() || other.isSpectator() || player.isAlliedTo(other) || other instanceof Player
			|| other instanceof ArmorStand) {
			return false;
		}
		if (other instanceof OwnableEntity pet && pet.getOwner() == player) {
			return false;
		}
		return other instanceof Enemy || other instanceof Mob mob && mob.getTarget() == player;
	}

	private static void cleave(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		List<LivingEntity> beside = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0),
			e -> e != target && isFoe(player, e));
		if (beside.isEmpty() || amount <= 0) {
			return;
		}
		int struck = 0;
		splashing = true;
		try {
			for (LivingEntity e : beside) {
				if (e.hurtServer(level, player.damageSources().playerAttack(player), amount)) {
					struck++;
				}
			}
		} finally {
			splashing = false;
		}
		if (struck > 0) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1f, 0.7f);
			HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.CLEAVE, struck);
		}
	}

	/** Earthshaker: every foe within 4 blocks is thrown back and dazed; ten seconds between slams. */
	private static void earthshaker(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		if (now < EARTHSHAKER_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		EARTHSHAKER_READY.put(player.getUUID(), now + HeavyRules.EARTHSHAKER_COOLDOWN);
		double r = HeavyRules.EARTHSHAKER_RADIUS;
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
			e -> e.distanceToSqr(player) <= r * r && isFoe(player, e));
		for (LivingEntity foe : foes) {
			Vec3 away = foe.position().subtract(player.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			double resist = 1.0 - foe.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (resist > 0) {
				foe.push(away.x * 1.2 * resist, 0.45 * resist, away.z * 1.2 * resist);
				if (foe instanceof ServerPlayer other) {
					other.connection.send(new ClientboundSetEntityMotionPacket(other));
				}
			}
			Stagger.stagger(foe, HeavyRules.EARTHSHAKER_STAGGER_TICKS);
		}
		// The mace's own ground-smash dust and the heavy smash sound, whatever the weapon.
		level.levelEvent(2013, player.getOnPos(), 750);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, player.getSoundSource(), 1f, 0.8f);
		HeavyWeaponsSkill.chip(player, HeavyWeaponsSkill.EARTHSHAKER, foes.size());
	}

	/** Ticks until Earthshaker can slam again (0 when ready). */
	public static int earthshakerCooldown(ServerPlayer player) {
		return (int) Math.max(0, EARTHSHAKER_READY.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime());
	}

	/** Whether an axe or mace jump-crit gets Overhead Blow (decided as vanilla multiplies the critical). */
	public static boolean overhead(HeavyHit hit) {
		return hit.crit && (hit.axe || hit.player.getMainHandItem().is(Items.MACE)) && Perks.has(hit.player, HeavyWeaponsSkill.OVERHEAD_BLOW);
	}

	public static void forget(UUID player) {
		EARTHSHAKER_READY.remove(player);
	}
}
