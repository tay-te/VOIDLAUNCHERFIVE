package dev.voidmc.mettle.character.skill.defense;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * A dazed mob (Shield Bash, Juggernaut) stands reeling for a moment: it cannot move and its blows don't land. Movement
 * is a Slowness strong enough to stop it (so players see the swirl and the timer is vanilla's); the blows are cancelled
 * here. Server thread only.
 */
public final class Daze {
	/** Slowness VII takes away 105 % of a mob's speed, which vanilla clamps to standing still. */
	private static final int SLOWNESS_AMPLIFIER = 6;
	private static final Map<Entity, Long> DAZED_UNTIL = new WeakHashMap<>();

	private Daze() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// A dazed mob's own swing (not its arrows, already in flight) misses.
			Entity attacker = source.getEntity();
			return attacker == null || attacker != source.getDirectEntity() || !isDazed(attacker);
		});
	}

	public static void apply(Mob mob, int ticks) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		DAZED_UNTIL.put(mob, level.getGameTime() + ticks);
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, SLOWNESS_AMPLIFIER));
		mob.getNavigation().stop();
		level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 8, 0.3, 0.1, 0.3, 0.1);
	}

	public static boolean isDazed(Entity entity) {
		Long until = DAZED_UNTIL.get(entity);
		if (until == null) {
			return false;
		}
		if (entity.level().getGameTime() >= until || !(entity instanceof LivingEntity living) || !living.isAlive()) {
			DAZED_UNTIL.remove(entity);
			return false;
		}
		return true;
	}
}
