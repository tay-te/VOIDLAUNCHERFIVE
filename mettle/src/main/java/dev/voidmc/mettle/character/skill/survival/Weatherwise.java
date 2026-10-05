package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;

/**
 * Weatherwise (feat): two minutes before rain or a storm reaches the overworld, a far-off rumble only you hear. Read off
 * the overworld's weather clock once a second.
 */
public final class Weatherwise {
	public static final int INTERVAL = 20;

	private static final Map<UUID, Long> WARNED_AT = new HashMap<>();

	private Weatherwise() {
	}

	public static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		WeatherData w = overworld.getWeatherData();
		int rain = SurvivalRules.ticksUntilRain(w.getClearWeatherTime(), w.getRainTime(), w.isRaining());
		int storm = SurvivalRules.ticksUntilStorm(w.getClearWeatherTime(), w.getRainTime(), w.isRaining(), w.getThunderTime(), w.isThundering());
		boolean stormSoon = SurvivalRules.warnNow(storm, INTERVAL);
		boolean rainSoon = SurvivalRules.warnNow(rain, INTERVAL);
		if (!stormSoon && !rainSoon) {
			return;
		}
		for (ServerPlayer player : overworld.players()) {
			if (Perks.has(player, SurvivalSkill.WEATHERWISE) && !player.isSpectator()) {
				warn(player, stormSoon);
			}
		}
	}

	private static void warn(ServerPlayer player, boolean storm) {
		WARNED_AT.put(player.getUUID(), player.level().getGameTime());
		// Low and far: thunder from somewhere over the horizon, quieter for plain rain.
		Vec3 away = player.getEyePosition().add(Vec3.directionFromRotation(0, player.getRandom().nextFloat() * 360f).scale(12));
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.LIGHTNING_BOLT_THUNDER),
			SoundSource.WEATHER, away.x, away.y, away.z, storm ? 0.5f : 0.3f, 0.5f, player.getRandom().nextLong()));
		SurvivalSkill.chip(player, SurvivalSkill.WEATHERWISE, storm ? "weatherwise.storm" : "weatherwise.rain");
	}

	/** Test hook: game time of the player's last weather warning, or -1. */
	public static long warnedAt(ServerPlayer player) {
		return WARNED_AT.getOrDefault(player.getUUID(), -1L);
	}

	public static void forget(UUID player) {
		WARNED_AT.remove(player);
	}
}
