package dev.voidmc.mettle.character.skill.heavy;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.mixin.heavyweapons.CreeperAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;

/**
 * What a heavy blow leaves behind on its target, all short-lived and server-side: a <b>stagger</b> (the mob stands
 * dazed, its attacks miss, a creeper's fuse goes out, a skeleton loses its draw), a <b>missed next attack</b>
 * (Stagger), and Bone Breaker's <b>slow</b>. Nothing here is saved; a reload simply ends it early.
 */
public final class Stagger {
	public static final Identifier BONE_BREAKER_SLOW = Mettle.id("heavy_weapons/bone_breaker/movement_speed");
	/** How long a "miss your next attack" lasts if the mob never swings. */
	private static final int MISS_WINDOW = 100;

	private static final Map<Mob, Long> STAGGERED = new HashMap<>();
	private static final Map<Mob, Long> MISS_NEXT = new HashMap<>();
	private static final Map<LivingEntity, Long> SLOWED = new HashMap<>();

	private Stagger() {
	}

	/** Dazes a mob for {@code ticks}: it stops where it is, drops its draw or fuse, and cannot land a melee hit. */
	public static void stagger(LivingEntity target, int ticks) {
		if (!(target instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
			return;
		}
		long until = level.getGameTime() + ticks;
		STAGGERED.merge(mob, until, Math::max);
		interrupt(mob);
		level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 8, 0.3, 0.1, 0.3, 0.1);
	}

	/** Stagger: the mob's next melee attack misses (and its current wind-up is interrupted). */
	public static void missNext(LivingEntity target) {
		if (target instanceof Mob mob && mob.level() instanceof ServerLevel level) {
			MISS_NEXT.put(mob, level.getGameTime() + MISS_WINDOW);
			interrupt(mob);
		}
	}

	/** Bone Breaker: 40 % slower for three seconds (a fresh hit restarts the clock). */
	public static void slow(LivingEntity target, int ticks) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		if (!speed.hasModifier(BONE_BREAKER_SLOW)) {
			speed.addTransientModifier(new AttributeModifier(BONE_BREAKER_SLOW, HeavyRules.BONE_BREAKER_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		SLOWED.put(target, level.getGameTime() + ticks);
	}

	public static void clearSlow(LivingEntity target) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(BONE_BREAKER_SLOW);
		}
		SLOWED.remove(target);
	}

	public static boolean isStaggered(Mob mob) {
		Long until = STAGGERED.get(mob);
		return until != null && mob.level().getGameTime() < until;
	}

	/** Called as a mob swings: true when the swing should miss (staggered, or owed a miss). */
	public static boolean cancelsAttack(Mob mob) {
		if (isStaggered(mob)) {
			return true;
		}
		Long until = MISS_NEXT.remove(mob);
		return until != null && mob.level().getGameTime() < until;
	}

	private static void interrupt(Mob mob) {
		mob.getNavigation().stop();
		if (mob.isUsingItem()) {
			mob.stopUsingItem();
		}
		if (mob instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
			((CreeperAccessor) creeper).mettle$heavySetSwell(0);
		}
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<Mob, Long>> it = STAGGERED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Long> e = it.next();
			Mob mob = e.getKey();
			if (mob.isRemoved() || !mob.isAlive() || mob.level().getGameTime() >= e.getValue()) {
				it.remove();
			} else {
				// Goals run before this, so undo what they started this tick: walking, drawing, fusing.
				interrupt(mob);
				mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			}
		}
		MISS_NEXT.entrySet().removeIf(e -> e.getKey().isRemoved() || e.getKey().level().getGameTime() >= e.getValue());
		for (Iterator<Map.Entry<LivingEntity, Long>> it = SLOWED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Long> e = it.next();
			LivingEntity target = e.getKey();
			if (target.isRemoved() || target.level().getGameTime() >= e.getValue()) {
				AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(BONE_BREAKER_SLOW);
				}
				it.remove();
			}
		}
	}

	/** Server stop: nothing carries over to the next world. */
	public static void clear() {
		STAGGERED.clear();
		MISS_NEXT.clear();
		SLOWED.clear();
	}
}
