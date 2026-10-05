package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;

/**
 * Whirlwind (mastery): a sneaking full swing with a blade spins, hitting everything else within 3 blocks for the same
 * damage as the swing (every 8 s). Your own pets and team mates are spared. Like vanilla's sweep, it costs no extra
 * durability; unlike it, it reaches all the way round.
 */
public final class Whirlwind {
	private Whirlwind() {
	}

	/**
	 * After a swing or a spear stab: {@code strength} is how charged it was, {@code damage} what it dealt to the foe you
	 * aimed at ({@code aimed}, spared the spin; null for a stab, whose foes are already in their hurt cooldown).
	 */
	public static void spin(ServerPlayer player, @Nullable Entity aimed, float strength, float damage) {
		if (damage <= 0 || strength < 0.9f || !player.isShiftKeyDown() || Combat.melee(player.getMainHandItem()) != Combat.Weapon.BLADE
			|| !Perks.has(player, BladesSkill.WHIRLWIND)) {
			return;
		}
		BladeState state = BladeState.of(player);
		long now = player.level().getGameTime();
		if (now < state.whirlwindReady) {
			return;
		}
		ServerLevel level = player.level();
		double r = BladeMath.WHIRLWIND_RADIUS;
		List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 1.0, r), e -> e != player
			&& e != aimed && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !(e instanceof ArmorStand stand && stand.isMarker())
			&& !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)) && e.distanceToSqr(player) <= (r + 0.5) * (r + 0.5));
		if (around.isEmpty()) {
			return;
		}
		DamageSource source = player.getMainHandItem().getDamageSource(player);
		int hit = 0;
		for (LivingEntity e : around) {
			if (e.hurtServer(level, source, damage)) {
				hit++;
				e.knockback(0.5, player.getX() - e.getX(), player.getZ() - e.getZ(), source, damage);
			}
		}
		state.whirlwindReady = now + BladeMath.WHIRLWIND_COOLDOWN;
		state.whirlwindAnnounce = true;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(a) * 1.8, player.getY(0.5), player.getZ() + Math.sin(a) * 1.8, 0, 0, 0, 0, 0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
		BladesSkill.chip(player, BladesSkill.WHIRLWIND, "whirlwind", hit);
	}

	/** Every half second: says once when the spin is ready again. */
	public static void announce(ServerPlayer player) {
		BladeState state = BladeState.of(player);
		if (state.whirlwindAnnounce && player.level().getGameTime() >= state.whirlwindReady) {
			state.whirlwindAnnounce = false;
			BladesSkill.chip(player, BladesSkill.WHIRLWIND, "whirlwind.ready");
		}
	}

	/** Ticks until the spin is ready (0 when ready), for the overlay and the journey. */
	public static long cooldown(ServerPlayer player) {
		return Math.max(0, BladeState.of(player).whirlwindReady - player.level().getGameTime());
	}
}
