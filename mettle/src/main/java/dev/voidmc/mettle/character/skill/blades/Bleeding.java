package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bleed (Skirmisher): wounds opened by critical blade hits, one damage a second each. The damage is mettle:bleed (through
 * armour, no knockback) and credited to whoever opened the last wound, so a mob that bleeds out still counts as theirs.
 * A bleed tick ignores and restores the foe's hurt cooldown, so it never eats the next sword hit or is eaten by it.
 */
public final class Bleeding {
	private static final Map<LivingEntity, Bleed> BLEEDING = new IdentityHashMap<>();
	private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A0A0A, 1.0f);

	private static final class Bleed {
		final BladeMath.Wounds wounds = new BladeMath.Wounds();
		UUID attacker;
	}

	private Bleeding() {
	}

	/** Opens a wound on {@code target}; returns how many are open. */
	public static int open(ServerPlayer attacker, LivingEntity target) {
		Bleed bleed = BLEEDING.computeIfAbsent(target, t -> new Bleed());
		bleed.attacker = attacker.getUUID();
		return bleed.wounds.open();
	}

	/** Open wounds on {@code target} (for the journey test and the overlay). */
	public static int wounds(LivingEntity target) {
		Bleed bleed = BLEEDING.get(target);
		return bleed == null ? 0 : bleed.wounds.count();
	}

	/** Once a second. */
	public static void tick(MinecraftServer server) {
		if (BLEEDING.isEmpty()) {
			return;
		}
		// A bleed tick can kill, and a death runs other mods' code; iterate a copy so nothing downstream can trip us.
		for (LivingEntity target : List.copyOf(BLEEDING.keySet())) {
			Bleed bleed = BLEEDING.get(target);
			int damage = bleed.wounds.tick();
			if (damage > 0 && target.isAlive() && !target.isRemoved() && target.level() instanceof ServerLevel level) {
				ServerPlayer attacker = server.getPlayerList().getPlayer(bleed.attacker);
				DamageSource source = level.damageSources().source(BleedDamage.BLEED, null, attacker);
				BladesLivingAccess access = (BladesLivingAccess) target;
				int cooldown = target.damageCooldownTime;
				float lastHurt = access.mettle$lastHurt();
				target.damageCooldownTime = 0;
				target.hurtServer(level, source, damage);
				if (target.isAlive()) {
					target.damageCooldownTime = cooldown;
					access.mettle$setLastHurt(lastHurt);
				}
				level.sendParticles(BLOOD, target.getX(), target.getY(0.6), target.getZ(), 4 * damage, 0.2, 0.3, 0.2, 0.0);
			}
			if (bleed.wounds.healed() || !target.isAlive() || target.isRemoved()) {
				BLEEDING.remove(target);
			}
		}
	}

	public static void clear() {
		BLEEDING.clear();
	}
}
