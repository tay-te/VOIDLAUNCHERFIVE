package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * What happens after a hit or a kill: Executioner's execution, Cut and Run, the Unbroken streak and Giant Slayer tally,
 * and the shield perks (Sword and Board, Master Strike's counter window).
 */
public final class Aftermath {
	/** Feat tallies: the best flawless blade streak, and large mobs felled with a blade. */
	public static final String STREAK = "blades_flawless_streak";
	public static final String LARGE_KILLS = "blades_large_kills";
	private static final float EXECUTE = 1.0e6f;

	private static boolean executing;

	private Aftermath() {
	}

	/** True while an execution's killing blow is dealt, so the hit bonuses stay out of it. */
	public static boolean executing() {
		return executing;
	}

	/** Fabric's AFTER_DAMAGE (only for entities still alive). */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (entity instanceof ServerPlayer player) {
			BladeState state = BladeState.of(player);
			long now = player.level().getGameTime();
			if (taken > 0) {
				state.lastHurt = now;
				state.streak = 0;
			}
			if (blocked) {
				blocked(player, state, now);
			}
		}
		if (!executing && source.getEntity() instanceof ServerPlayer attacker && attacker != entity
			&& Combat.weapon(source, attacker) == Combat.Weapon.BLADE && !BladeHits.bossOrPlayer(entity)
			&& BladeMath.executes(entity.getHealth(), entity.getMaxHealth()) && Perks.has(attacker, BladesSkill.EXECUTIONER)) {
			execute(attacker, entity, source);
		}
	}

	/** A shield took a hit. */
	private static void blocked(ServerPlayer player, BladeState state, long now) {
		if (Perks.has(player, BladesSkill.MASTER_STRIKE)) {
			state.counterUntil = now + BladeMath.COUNTER_WINDOW;
		}
		if (Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE && Perks.has(player, BladesSkill.SWORD_AND_BOARD)) {
			Swings.charge(player);
			BladesSkill.chip(player, BladesSkill.SWORD_AND_BOARD, "sword_and_board");
		}
	}

	/** The killing blow: the same weapon hit again, through the hurt cooldown, for far more than anything has. */
	private static void execute(ServerPlayer attacker, LivingEntity target, DamageSource source) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		executing = true;
		try {
			target.damageCooldownTime = 0;
			target.hurtServer(level, source, EXECUTE);
		} finally {
			executing = false;
		}
		if (target.isDeadOrDying()) {
			attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1));
			BladesSkill.chip(attacker, BladesSkill.EXECUTIONER, "executioner");
		}
	}

	/** Fabric's AFTER_KILLED_OTHER_ENTITY. */
	public static void afterKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (!(killer instanceof ServerPlayer player) || Combat.weapon(source, player) != Combat.Weapon.BLADE) {
			return;
		}
		if (!player.isCreative()) {
			BladeState state = BladeState.of(player);
			if (killed instanceof Enemy) {
				state.streak++;
				long best = FeatCounters.get(player, STREAK);
				if (state.streak > best) {
					FeatCounters.add(player, STREAK, state.streak - best);
				}
			}
			if (BladeHits.isLarge(killed)) {
				FeatCounters.add(player, LARGE_KILLS, 1);
			}
		}
		if (Perks.has(player, BladesSkill.CUT_AND_RUN)) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0));
			Swings.charge(player);
			BladesSkill.chip(player, BladesSkill.CUT_AND_RUN, "cut_and_run");
		}
	}

	/** The current kill streak without taking damage (for the journey test and the overlay). */
	public static int streak(ServerPlayer player) {
		return BladeState.of(player).streak;
	}
}
