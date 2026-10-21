package dev.voidmc.hearth.social.rhythm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/rhythm.json} (docs/LIVING_WORLD_2.md §2). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record RhythmConfig(Rhythm rhythm, Lights lights) {
	public static final RhythmConfig DEFAULTS = new RhythmConfig(Rhythm.DEFAULTS, Lights.DEFAULTS);

	public static final Codec<RhythmConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Rhythm.CODEC.optionalFieldOf("rhythm", Rhythm.DEFAULTS).forGetter(RhythmConfig::rhythm),
		Lights.CODEC.optionalFieldOf("lights", Lights.DEFAULTS).forGetter(RhythmConfig::lights)
	).apply(i, RhythmConfig::new));

	/**
	 * @param enabled the daily and weekly rhythm (services, wash day, tavern nights, watch rounds)
	 * @param passTicks how often embodied settlements are looked at
	 */
	public record Rhythm(boolean enabled, int passTicks) {
		public static final Rhythm DEFAULTS = new Rhythm(true, 20);
		public static final Codec<Rhythm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Rhythm::enabled),
			Codec.intRange(1, 1200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Rhythm::passTicks)
		).apply(i, Rhythm::new));
	}

	/**
	 * @param maxPerTown watch lights (blocks) lit in a town at night
	 */
	public record Lights(int maxPerTown) {
		public static final Lights DEFAULTS = new Lights(8);
		public static final Codec<Lights> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Lights::maxPerTown)
		).apply(i, Lights::new));
	}
}
