package dev.voidmc.mettle.character.skill.defense;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.DefenseSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * Path A, Shieldbearer: the shield, and the people behind it. Shield Up, Bulwark and Guardian change how blocking
 * itself works (called from {@code mixin.defense.LivingEntityMixin}); Shield Bash and Perfect Block react to hits.
 */
public final class Shieldbearer {
	/** When each Shield Bash player last lowered a raised shield. */
	private static final Map<UUID, Long> LOWERED_AT = new HashMap<>();

	private Shieldbearer() {
	}

	// ---------------------------------------------------------------- raising and holding (both sides)

	/**
	 * Shield Up: a shield's raise delay for this entity. Runs on the client as well (perks sync to their owner), so the
	 * shield is up the moment the player presses use, not a quarter second later.
	 */
	public static int blockDelay(LivingEntity entity, int delay) {
		return delay > 0 && entity instanceof Player player && Perks.has(player, DefenseSkill.SHIELD_UP) ? 0 : delay;
	}

	/** Shield Up: nothing knocks you back while you block. Returns whether this knockback is ignored. */
	public static boolean holdsGround(LivingEntity entity) {
		if (entity instanceof ServerPlayer player && player.isBlocking() && Perks.has(player, DefenseSkill.SHIELD_UP)) {
			DefenseSkill.feedback(player, DefenseSkill.SHIELD_UP, Component.translatable(DefenseSkill.chip(DefenseSkill.SHIELD_UP)), 100);
			return true;
		}
		return false;
	}

	/** Bulwark: the angle a hit is judged at, so a shield also covers the sides (everything but the back). */
	public static boolean hasBulwark(LivingEntity entity) {
		return entity instanceof ServerPlayer player && Perks.has(player, DefenseSkill.BULWARK);
	}

	public static void bulwarkCaught(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			DefenseSkill.feedback(player, DefenseSkill.BULWARK, Component.translatable(DefenseSkill.chip(DefenseSkill.BULWARK)), 40);
		}
	}

	// ---------------------------------------------------------------- Guardian

	/**
	 * Guardian: damage to someone non-hostile within 4 blocks of a player holding a shield up is cut by 40 %, and a
	 * hostile within 8 blocks of that player who threw the hit turns on them instead. Players, tamed animals,
	 * villagers and every other non-hostile mob count (Hearth folk among them once Hearth is present).
	 */
	public static float guarded(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0 || victim instanceof Enemy || !(victim instanceof Player || victim instanceof Mob)
			|| !(victim.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		for (ServerPlayer guardian : level.players()) {
			if (guardian == victim || guardian == source.getEntity() || !guardian.isBlocking()
				|| guardian.distanceToSqr(victim) > DefenseRules.GUARDIAN_ALLY_RANGE * DefenseRules.GUARDIAN_ALLY_RANGE
				|| !Perks.has(guardian, DefenseSkill.GUARDIAN)) {
				continue;
			}
			if (source.getEntity() instanceof Mob attacker && attacker instanceof Enemy && attacker.isAlive()
				&& attacker.distanceToSqr(guardian) <= DefenseRules.GUARDIAN_TAUNT_RANGE * DefenseRules.GUARDIAN_TAUNT_RANGE) {
				attacker.setTarget(guardian);
				// Brain-driven hostiles (piglin brutes, hoglins…) keep their target in memory rather than in the goal.
				if (attacker.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
					attacker.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, (LivingEntity) guardian);
				}
			}
			DefenseSkill.feedback(guardian, DefenseSkill.GUARDIAN, Component.translatable(DefenseSkill.chip(DefenseSkill.GUARDIAN), victim.getDisplayName()), 60);
			return damage * DefenseRules.GUARDIAN_SHARE;
		}
		return damage;
	}

	// ---------------------------------------------------------------- Shield Bash

	/** Called as any entity stops using an item: remembers a Shield Bash player lowering a working shield. */
	public static void lowered(LivingEntity entity) {
		if (entity instanceof ServerPlayer player && player.isBlocking() && Perks.has(player, DefenseSkill.SHIELD_BASH)) {
			ItemStack shield = player.getUseItem();
			// An axe that knocked the shield down puts it on cooldown first; that is not a bash.
			if (!player.getCooldowns().isOnCooldown(shield)) {
				LOWERED_AT.put(player.getUUID(), player.level().getGameTime());
			}
		}
	}

	/** After a player's hit lands: Shield Bash. */
	public static void afterHit(ServerPlayer attacker, LivingEntity target, DamageSource source) {
		if (source.getDirectEntity() != attacker || !(target instanceof Mob mob)) {
			return;
		}
		Long at = LOWERED_AT.remove(attacker.getUUID());
		if (at == null || attacker.level().getGameTime() - at > DefenseRules.SHIELD_BASH_WINDOW || !Perks.has(attacker, DefenseSkill.SHIELD_BASH)) {
			return;
		}
		mob.knockback(DefenseRules.THROW_POWER, attacker.getX() - mob.getX(), attacker.getZ() - mob.getZ(), source, 0f);
		Daze.apply(mob, DefenseRules.DAZE_TICKS);
		attacker.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 0.8f);
		DefenseSkill.feedback(attacker, DefenseSkill.SHIELD_BASH, Component.translatable(DefenseSkill.chip(DefenseSkill.SHIELD_BASH), mob.getDisplayName()));
	}

	// ---------------------------------------------------------------- Perfect Block

	/** After a player takes (or turns) a hit. */
	public static void afterBlock(ServerPlayer defender, DamageSource source, float baseTaken, float taken, boolean blocked) {
		if (!blocked || !(source.getEntity() instanceof LivingEntity attacker) || attacker == defender || !attacker.isAlive()
			|| !Perks.has(defender, DefenseSkill.PERFECT_BLOCK)) {
			return;
		}
		ItemStack shield = defender.getItemBlockingWith();
		BlocksAttacks blocks = shield == null ? null : shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || !DefenseRules.perfectBlock(defender.getTicksUsingItem(), blockDelay(defender, blocks.blockDelayTicks()))) {
			return;
		}
		float reflected = baseTaken * DefenseRules.PERFECT_BLOCK_SHARE;
		// Thorns, caused by the defender but with no direct hitter, so no weapon skill learns from it.
		DamageSource back = new DamageSource(defender.damageSources().thorns(defender).typeHolder(), null, defender);
		attacker.hurtServer(defender.level(), back, reflected);
		defender.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.7f);
		DefenseSkill.feedback(defender, DefenseSkill.PERFECT_BLOCK, Component.translatable(DefenseSkill.chip(DefenseSkill.PERFECT_BLOCK),
			String.format(java.util.Locale.ROOT, "%.1f", reflected)));
	}

	public static void forget(UUID player) {
		LOWERED_AT.remove(player);
	}
}
