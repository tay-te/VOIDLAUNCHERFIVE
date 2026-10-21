package dev.voidmc.hearth.sim.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/wild.json} (docs/LIVING_WORLD_2.md §7). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record WildConfig(Wild wild) {
	public static final WildConfig DEFAULTS = new WildConfig(Wild.DEFAULTS);

	public static final Codec<WildConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Wild.CODEC.optionalFieldOf("wild", Wild.DEFAULTS).forGetter(WildConfig::wild)
	).apply(i, WildConfig::new));

	/**
	 * @param enabled posts in the wild (inns, toll gates, hospices, hermitages)
	 * @param perRegion posts sited per atlas region
	 * @param stepInterval ticks between steps of a post
	 * @param maxFolkPerPost folk embodied at one post (within the town caps)
	 */
	public record Wild(boolean enabled, int perRegion, int stepInterval, int maxFolkPerPost) {
		public static final Wild DEFAULTS = new Wild(true, 3, 6000, 12);
		public static final Codec<Wild> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Wild::enabled),
			Codec.intRange(0, 32).optionalFieldOf("per_region", DEFAULTS.perRegion).forGetter(Wild::perRegion),
			Codec.intRange(20, 240000).optionalFieldOf("step_interval", DEFAULTS.stepInterval).forGetter(Wild::stepInterval),
			Codec.intRange(0, 64).optionalFieldOf("max_folk_per_post", DEFAULTS.maxFolkPerPost).forGetter(Wild::maxFolkPerPost)
		).apply(i, Wild::new));
	}
}
