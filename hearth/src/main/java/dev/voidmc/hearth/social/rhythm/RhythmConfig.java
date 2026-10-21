package dev.voidmc.hearth.social.rhythm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/rhythm.json} (docs/LIVING_WORLD_2.md §2). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record RhythmConfig(Rhythm rhythm, Lights lights, Service service, Tavern tavern, Wash wash, Smoke smoke) {
	public static final RhythmConfig DEFAULTS = new RhythmConfig(Rhythm.DEFAULTS, Lights.DEFAULTS, Service.DEFAULTS, Tavern.DEFAULTS, Wash.DEFAULTS,
		Smoke.DEFAULTS);

	public static final Codec<RhythmConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Rhythm.CODEC.optionalFieldOf("rhythm", Rhythm.DEFAULTS).forGetter(RhythmConfig::rhythm),
		Lights.CODEC.optionalFieldOf("lights", Lights.DEFAULTS).forGetter(RhythmConfig::lights),
		Service.CODEC.optionalFieldOf("service", Service.DEFAULTS).forGetter(RhythmConfig::service),
		Tavern.CODEC.optionalFieldOf("tavern", Tavern.DEFAULTS).forGetter(RhythmConfig::tavern),
		Wash.CODEC.optionalFieldOf("wash", Wash.DEFAULTS).forGetter(RhythmConfig::wash),
		Smoke.CODEC.optionalFieldOf("smoke", Smoke.DEFAULTS).forGetter(RhythmConfig::smoke)
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
	 * @param level      the light's level (the watchman's torch)
	 * @param changesPerSecond block changes a town's lights may make a second
	 */
	public record Lights(int maxPerTown, int level, int changesPerSecond) {
		public static final Lights DEFAULTS = new Lights(8, 14, 8);
		public static final Codec<Lights> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Lights::maxPerTown),
			Codec.intRange(1, 15).optionalFieldOf("level", DEFAULTS.level).forGetter(Lights::level),
			Codec.intRange(1, 64).optionalFieldOf("changes_per_second", DEFAULTS.changesPerSecond).forGetter(Lights::changesPerSecond)
		).apply(i, Lights::new));
	}

	/**
	 * @param offering    bits a head attending the rest day's service puts in the plate (to the treasury, once a rest day)
	 * @param standing    the standing deed's weight for attending (once a week a town)
	 * @param sermonTicks between the priest's lines
	 */
	public record Service(boolean enabled, int offering, int standing, int sermonTicks) {
		public static final Service DEFAULTS = new Service(true, 1, 1, 400);
		public static final Codec<Service> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Service::enabled),
			Codec.intRange(0, 100).optionalFieldOf("offering", DEFAULTS.offering).forGetter(Service::offering),
			Codec.intRange(0, 25).optionalFieldOf("standing", DEFAULTS.standing).forGetter(Service::standing),
			Codec.intRange(40, 6000).optionalFieldOf("sermon_ticks", DEFAULTS.sermonTicks).forGetter(Service::sermonTicks)
		).apply(i, Service::new));
	}

	/**
	 * @param share    the chance an adult drinks at the tavern on a night (the sociable twice it)
	 * @param round    bits a head for a round bought
	 * @param standing the standing deed's weight for buying a round
	 */
	public record Tavern(boolean enabled, double share, int round, int standing) {
		public static final Tavern DEFAULTS = new Tavern(true, 0.22, 2, 2);
		public static final Codec<Tavern> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Tavern::enabled),
			Codec.doubleRange(0, 1).optionalFieldOf("share", DEFAULTS.share).forGetter(Tavern::share),
			Codec.intRange(0, 100).optionalFieldOf("round", DEFAULTS.round).forGetter(Tavern::round),
			Codec.intRange(0, 25).optionalFieldOf("standing", DEFAULTS.standing).forGetter(Tavern::standing)
		).apply(i, Tavern::new));
	}

	/**
	 * @param households the share of households that send someone to wash on wash day
	 * @param least      at least this many washers where a town has them
	 * @param reach      how far beyond the town's bounds folk go for water
	 * @param scanReads  block reads the one-time search for wash places may take a pass
	 * @param lines      laundry lines put up at most in a town
	 */
	public record Wash(boolean enabled, double households, int least, int reach, int scanReads, int lines) {
		public static final Wash DEFAULTS = new Wash(true, 0.25, 3, 48, 400, 6);
		public static final Codec<Wash> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Wash::enabled),
			Codec.doubleRange(0, 1).optionalFieldOf("households", DEFAULTS.households).forGetter(Wash::households),
			Codec.intRange(0, 32).optionalFieldOf("least", DEFAULTS.least).forGetter(Wash::least),
			Codec.intRange(0, 128).optionalFieldOf("reach", DEFAULTS.reach).forGetter(Wash::reach),
			Codec.intRange(10, 4000).optionalFieldOf("scan_reads", DEFAULTS.scanReads).forGetter(Wash::scanReads),
			Codec.intRange(0, 32).optionalFieldOf("lines", DEFAULTS.lines).forGetter(Wash::lines)
		).apply(i, Wash::new));
	}

	/**
	 * @param chimneys chimneys smoking in a town at most
	 * @param everyTicks how often they puff
	 */
	public record Smoke(boolean enabled, int chimneys, int everyTicks) {
		public static final Smoke DEFAULTS = new Smoke(true, 24, 40);
		public static final Codec<Smoke> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Smoke::enabled),
			Codec.intRange(0, 128).optionalFieldOf("chimneys", DEFAULTS.chimneys).forGetter(Smoke::chimneys),
			Codec.intRange(10, 1200).optionalFieldOf("every_ticks", DEFAULTS.everyTicks).forGetter(Smoke::everyTicks)
		).apply(i, Smoke::new));
	}
}
