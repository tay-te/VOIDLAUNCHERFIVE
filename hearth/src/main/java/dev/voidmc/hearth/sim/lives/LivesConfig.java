package dev.voidmc.hearth.sim.lives;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/lives.json} (docs/LIVING_WORLD_2.md §4). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record LivesConfig(Lives lives) {
	public static final LivesConfig DEFAULTS = new LivesConfig(Lives.DEFAULTS);

	public static final Codec<LivesConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Lives.CODEC.optionalFieldOf("lives", Lives.DEFAULTS).forGetter(LivesConfig::lives)
	).apply(i, LivesConfig::new));

	/**
	 * @param enabled named characters, relations, apprentices and feuds
	 * @param maxNotablesPerTown named characters a town keeps
	 */
	public record Lives(boolean enabled, int maxNotablesPerTown) {
		public static final Lives DEFAULTS = new Lives(true, 6);
		public static final Codec<Lives> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Lives::enabled),
			Codec.intRange(0, 64).optionalFieldOf("max_notables_per_town", DEFAULTS.maxNotablesPerTown).forGetter(Lives::maxNotablesPerTown)
		).apply(i, Lives::new));
	}
}
