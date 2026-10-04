package dev.voidmc.hearth.social.service;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialModule;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The bathhouse: a hot bath washes off the road — the dirt, the blood and the smell that put folk off (the standing
 * module's cleanliness, reset through a {@code social:bath} deed and {@link PlayerLife#lastBath}), sickness from bad
 * food, and the chill — and leaves you warm for a while.
 */
public final class Bathhouse {
	private Bathhouse() {
	}

	public static boolean bathe(ServerPlayer player, Settlement s, Resident keeper) {
		SocialConfig.Bath cfg = SocialModule.config().bath();
		if (!Hooks.charge(player, cfg.price(), keeper, s)) {
			return false;
		}
		s.deposit(cfg.price());
		s.markDirty();
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.HUNGER);
		player.removeEffect(MobEffects.NAUSEA);
		player.clearFire();
		player.setTicksFrozen(0);
		if (cfg.regenSeconds() > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, cfg.regenSeconds() * 20, 0), null);
		}
		ServerLevel level = (ServerLevel) player.level();
		PlayerLife.set(player, PlayerLife.of(player).withBath(level.getGameTime()));
		Hooks.deed(player, s, "social:bath", 0, keeper.id, player.blockPosition(), true);
		// the bath itself: at the tub if the bathhouse has one in sight, else where the player stands
		List<Poi> baths = s.pois(PoiType.BATH);
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		for (Poi b : baths) {
			if (b.pos().distSqr(player.blockPosition()) <= 16 * 16) {
				x = b.pos().getX() + 0.5;
				y = b.pos().getY() + 0.2;
				z = b.pos().getZ() + 0.5;
				break;
			}
		}
		level.sendParticles(ParticleTypes.SPLASH, x, y + 0.5, z, 40, 0.6, 0.3, 0.6, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, x, y + 1.0, z, 12, 0.5, 0.4, 0.5, 0.01);
		level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}
}
