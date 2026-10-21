package dev.voidmc.hearth.sim.lives;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/lives.json} (docs/LIVING_WORLD_2.md §4). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record LivesConfig(Lives lives, Crafts crafts, Relations relations, Feuds feuds, Talk talk) {
	public static final LivesConfig DEFAULTS = new LivesConfig(Lives.DEFAULTS, Crafts.DEFAULTS, Relations.DEFAULTS, Feuds.DEFAULTS, Talk.DEFAULTS);

	public static final Codec<LivesConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Lives.CODEC.optionalFieldOf("lives", Lives.DEFAULTS).forGetter(LivesConfig::lives),
		Crafts.CODEC.optionalFieldOf("crafts", Crafts.DEFAULTS).forGetter(LivesConfig::crafts),
		Relations.CODEC.optionalFieldOf("relations", Relations.DEFAULTS).forGetter(LivesConfig::relations),
		Feuds.CODEC.optionalFieldOf("feuds", Feuds.DEFAULTS).forGetter(LivesConfig::feuds),
		Talk.CODEC.optionalFieldOf("talk", Talk.DEFAULTS).forGetter(LivesConfig::talk)
	).apply(i, LivesConfig::new));

	/**
	 * @param enabled            named characters, relations, apprentices and feuds
	 * @param maxNotablesPerTown named characters a town keeps (a hamlet 2, a village 3, a town 5, a city 6, never more)
	 * @param griefDays          days a widow or widower mourns
	 */
	public record Lives(boolean enabled, int maxNotablesPerTown, int griefDays) {
		public static final Lives DEFAULTS = new Lives(true, 6, 7);
		public static final Codec<Lives> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Lives::enabled),
			Codec.intRange(0, 64).optionalFieldOf("max_notables_per_town", DEFAULTS.maxNotablesPerTown).forGetter(Lives::maxNotablesPerTown),
			Codec.intRange(0, 100).optionalFieldOf("grief_days", DEFAULTS.griefDays).forGetter(Lives::griefDays)
		).apply(i, Lives::new));
	}

	/**
	 * @param apprenticeAge      days old a child is when apprenticed (children grow up at the sim's {@code childhood_days})
	 * @param masterDays         days a journeyman works the trade before the masterpiece
	 * @param apprenticesPerMaster how many apprentices one master takes on
	 */
	public record Crafts(int apprenticeAge, int masterDays, int apprenticesPerMaster) {
		public static final Crafts DEFAULTS = new Crafts(28, 60, 1);
		public static final Codec<Crafts> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 10_000).optionalFieldOf("apprentice_age", DEFAULTS.apprenticeAge).forGetter(Crafts::apprenticeAge),
			Codec.intRange(1, 10_000).optionalFieldOf("master_days", DEFAULTS.masterDays).forGetter(Crafts::masterDays),
			Codec.intRange(1, 4).optionalFieldOf("apprentices_per_master", DEFAULTS.apprenticesPerMaster).forGetter(Crafts::apprenticesPerMaster)
		).apply(i, Crafts::new));
	}

	/**
	 * @param updatesPerDay    contacts a resident's relations take a day at most (workmates, neighbours, the evening's company)
	 * @param decayPerDay      strength a friendship loses each day without contact, after {@code graceDays}
	 * @param friendAt         strength at which acquaintances are friends
	 * @param sweetheartAt     friendship strength from which two unmarried adults may become sweethearts
	 * @param sweetheartChance the daily chance, once that strong
	 * @param rivalChance      the chance a third who was fond of one of them takes it badly
	 * @param sameSexShare     where the social module allows two men or two women to marry, roughly this share of courtships
	 */
	public record Relations(int updatesPerDay, int decayPerDay, int graceDays, int friendAt, int sweetheartAt, double sweetheartChance,
		double rivalChance, double sameSexShare) {
		public static final Relations DEFAULTS = new Relations(3, 1, 4, 40, 55, 0.08, 0.35, 0.1);
		public static final Codec<Relations> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 3).optionalFieldOf("updates_per_day", DEFAULTS.updatesPerDay).forGetter(Relations::updatesPerDay),
			Codec.intRange(0, 20).optionalFieldOf("decay_per_day", DEFAULTS.decayPerDay).forGetter(Relations::decayPerDay),
			Codec.intRange(0, 100).optionalFieldOf("grace_days", DEFAULTS.graceDays).forGetter(Relations::graceDays),
			Codec.intRange(1, 100).optionalFieldOf("friend_at", DEFAULTS.friendAt).forGetter(Relations::friendAt),
			Codec.intRange(1, 100).optionalFieldOf("sweetheart_at", DEFAULTS.sweetheartAt).forGetter(Relations::sweetheartAt),
			Codec.doubleRange(0, 1).optionalFieldOf("sweetheart_chance", DEFAULTS.sweetheartChance).forGetter(Relations::sweetheartChance),
			Codec.doubleRange(0, 1).optionalFieldOf("rival_chance", DEFAULTS.rivalChance).forGetter(Relations::rivalChance),
			Codec.doubleRange(0, 1).optionalFieldOf("same_sex_share", DEFAULTS.sameSexShare).forGetter(Relations::sameSexShare)
		).apply(i, Relations::new));
	}

	/**
	 * @param maxPerTown     feuds a town carries at once
	 * @param jiltedChance   the chance a jilted rival's house starts a feud with the sweetheart's
	 * @param blameChance    the chance an unwitnessed theft from a resident is blamed on a neighbouring house
	 * @param boundaryChance the daily chance neighbours in a crowded town fall out over the line between their plots
	 * @param peacePerDay    the daily chance a feud is made up (grows with its age)
	 * @param brawlChance    the chance two feuding folk who meet at the tavern come to blows
	 */
	public record Feuds(int maxPerTown, double jiltedChance, double blameChance, double boundaryChance, double peacePerDay, double brawlChance) {
		public static final Feuds DEFAULTS = new Feuds(6, 0.45, 0.25, 0.004, 0.008, 0.15);
		public static final Codec<Feuds> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 32).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Feuds::maxPerTown),
			Codec.doubleRange(0, 1).optionalFieldOf("jilted_chance", DEFAULTS.jiltedChance).forGetter(Feuds::jiltedChance),
			Codec.doubleRange(0, 1).optionalFieldOf("blame_chance", DEFAULTS.blameChance).forGetter(Feuds::blameChance),
			Codec.doubleRange(0, 1).optionalFieldOf("boundary_chance", DEFAULTS.boundaryChance).forGetter(Feuds::boundaryChance),
			Codec.doubleRange(0, 1).optionalFieldOf("peace_per_day", DEFAULTS.peacePerDay).forGetter(Feuds::peacePerDay),
			Codec.doubleRange(0, 1).optionalFieldOf("brawl_chance", DEFAULTS.brawlChance).forGetter(Feuds::brawlChance)
		).apply(i, Feuds::new));
	}

	/**
	 * @param passTicks       how often the folk near each player are looked at for a named character's remark or talk about
	 *                        the player
	 * @param remarkChance    the chance a named character within range says something, a pass
	 * @param overheardChance the chance two folk at leisure near a player talk about them, a pass (only after a deed)
	 * @param radius          how near folk must be to the player
	 */
	public record Talk(int passTicks, double remarkChance, double overheardChance, int radius) {
		public static final Talk DEFAULTS = new Talk(40, 0.2, 0.12, 12);
		public static final Codec<Talk> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 1200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Talk::passTicks),
			Codec.doubleRange(0, 1).optionalFieldOf("remark_chance", DEFAULTS.remarkChance).forGetter(Talk::remarkChance),
			Codec.doubleRange(0, 1).optionalFieldOf("overheard_chance", DEFAULTS.overheardChance).forGetter(Talk::overheardChance),
			Codec.intRange(2, 32).optionalFieldOf("radius", DEFAULTS.radius).forGetter(Talk::radius)
		).apply(i, Talk::new));
	}
}
