package dev.voidmc.hearth.standing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The standing module's settings, {@code config/hearth/standing.json}. Every field is optional in the file; groups
 * mirror docs/STANDING.md.
 */
public record StandingConfig(Titles titles, Town town, Opinion opinion, Gossip gossip, Fame fame, Appearance appearance, Speech speech,
	Companies companies) {

	public static final StandingConfig DEFAULTS = new StandingConfig(Titles.DEFAULTS, Town.DEFAULTS, Opinion.DEFAULTS, Gossip.DEFAULTS, Fame.DEFAULTS,
		Appearance.DEFAULTS, Speech.DEFAULTS, Companies.DEFAULTS);

	public static final Codec<StandingConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Titles.CODEC.optionalFieldOf("titles", Titles.DEFAULTS).forGetter(StandingConfig::titles),
		Town.CODEC.optionalFieldOf("town", Town.DEFAULTS).forGetter(StandingConfig::town),
		Opinion.CODEC.optionalFieldOf("opinion", Opinion.DEFAULTS).forGetter(StandingConfig::opinion),
		Gossip.CODEC.optionalFieldOf("gossip", Gossip.DEFAULTS).forGetter(StandingConfig::gossip),
		Fame.CODEC.optionalFieldOf("fame", Fame.DEFAULTS).forGetter(StandingConfig::fame),
		Appearance.CODEC.optionalFieldOf("appearance", Appearance.DEFAULTS).forGetter(StandingConfig::appearance),
		Speech.CODEC.optionalFieldOf("speech", Speech.DEFAULTS).forGetter(StandingConfig::speech),
		Companies.CODEC.optionalFieldOf("companies", Companies.DEFAULTS).forGetter(StandingConfig::companies)
	).apply(i, StandingConfig::new));

	/**
	 * Standing thresholds for titles (docs/LIVING_WORLD.md §3). Between {@code distrusted} and {@code known} a player is a
	 * stranger.
	 *
	 * @param outlaw     at or below this, an outlaw (also whenever the law module says so)
	 * @param distrusted at or below this, distrusted
	 * @param known      at or above this, known
	 * @param friend     at or above this, a friend of the town
	 * @param hero       at or above this, a hero
	 * @param burgherMin owners of property in the town are burghers from this standing up (below it their title follows standing)
	 */
	public record Titles(int outlaw, int distrusted, int known, int friend, int hero, int burgherMin) {
		public static final Titles DEFAULTS = new Titles(-60, -20, 10, 40, 80, 0);
		public static final Codec<Titles> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(-100, 0).optionalFieldOf("outlaw", DEFAULTS.outlaw).forGetter(Titles::outlaw),
			Codec.intRange(-100, 0).optionalFieldOf("distrusted", DEFAULTS.distrusted).forGetter(Titles::distrusted),
			Codec.intRange(0, 100).optionalFieldOf("known", DEFAULTS.known).forGetter(Titles::known),
			Codec.intRange(0, 100).optionalFieldOf("friend", DEFAULTS.friend).forGetter(Titles::friend),
			Codec.intRange(0, 100).optionalFieldOf("hero", DEFAULTS.hero).forGetter(Titles::hero),
			Codec.intRange(-100, 100).optionalFieldOf("burgher_min", DEFAULTS.burgherMin).forGetter(Titles::burgherMin)
		).apply(i, Titles::new));
	}

	/**
	 * The settlement layer.
	 *
	 * @param softCap             how much harder standing is to move the further it already is from 0 (0 = linear; 0.5 = half
	 *                            as easy at ±100)
	 * @param negativeDriftPerDay points a day a bad name recovers toward 0 (towns forgive slowly)
	 * @param positiveDriftPerDay points a day a good name fades toward 0 (0 = never)
	 * @param unwitnessedBad      share of a bad deed's effect on the town when nobody saw it (0: only those involved know)
	 * @param unwitnessedGood     share of a good deed's effect on the town when it was not seen done (a quest handed in)
	 * @param seedFromSettlement  take a player's first standing from the sim's existing reputation numbers
	 * @param mirrorToSettlement  also move the sim's raw reputation numbers, for modules that still read them
	 * @param newsPerTown         deeds and gossip a town remembers for its folk to talk about
	 * @param newsMinWeight       smallest deed a town remembers as news
	 * @param arrivalLine         send the standing line shown under the arrival banner
	 * @param trackInterval       ticks between a player's checks (arrival, washing, title changes)
	 */
	public record Town(double softCap, double negativeDriftPerDay, double positiveDriftPerDay, double unwitnessedBad, double unwitnessedGood,
		boolean seedFromSettlement, boolean mirrorToSettlement, int newsPerTown, int newsMinWeight, boolean arrivalLine, int trackInterval) {
		public static final Town DEFAULTS = new Town(0.5, 0.25, 0.0, 0.0, 1.0, true, false, 12, 5, true, 20);
		public static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(0, 0.95).optionalFieldOf("soft_cap", DEFAULTS.softCap).forGetter(Town::softCap),
			Codec.doubleRange(0, 10).optionalFieldOf("negative_drift_per_day", DEFAULTS.negativeDriftPerDay).forGetter(Town::negativeDriftPerDay),
			Codec.doubleRange(0, 10).optionalFieldOf("positive_drift_per_day", DEFAULTS.positiveDriftPerDay).forGetter(Town::positiveDriftPerDay),
			Codec.doubleRange(0, 1).optionalFieldOf("unwitnessed_bad", DEFAULTS.unwitnessedBad).forGetter(Town::unwitnessedBad),
			Codec.doubleRange(0, 1).optionalFieldOf("unwitnessed_good", DEFAULTS.unwitnessedGood).forGetter(Town::unwitnessedGood),
			Codec.BOOL.optionalFieldOf("seed_from_settlement", DEFAULTS.seedFromSettlement).forGetter(Town::seedFromSettlement),
			Codec.BOOL.optionalFieldOf("mirror_to_settlement", DEFAULTS.mirrorToSettlement).forGetter(Town::mirrorToSettlement),
			Codec.intRange(0, 64).optionalFieldOf("news_per_town", DEFAULTS.newsPerTown).forGetter(Town::newsPerTown),
			Codec.intRange(0, 100).optionalFieldOf("news_min_weight", DEFAULTS.newsMinWeight).forGetter(Town::newsMinWeight),
			Codec.BOOL.optionalFieldOf("arrival_line", DEFAULTS.arrivalLine).forGetter(Town::arrivalLine),
			Codec.intRange(5, 200).optionalFieldOf("track_interval", DEFAULTS.trackInterval).forGetter(Town::trackInterval)
		).apply(i, Town::new));
	}

	/**
	 * The personal layer: each resident's memories of players.
	 *
	 * @param memories          memories a resident keeps (the faintest is forgotten first)
	 * @param halfLifeDays      days for a memory to fade to half
	 * @param strongThreshold   memories at least this strong (|weight|) fade slower
	 * @param strongMultiplier  how much slower
	 * @param townInfluence     share of the town's standing that colours each resident's opinion
	 * @param mergeDays         the same kind of deed by the same player within this many days is one memory (the stronger)
	 * @param witnessRadius     blocks within which embodied folk can witness a deed
	 * @param maxWitnesses      witnesses remembered per deed
	 * @param familyShare       a deed against someone also touches their family, at this share (times the deed table's family factor)
	 * @param hearsayPerDay     times a day someone in a town passes what they remember of a player on to a friend
	 * @param hearsayShare      how much of the memory the friend takes from hearing it
	 */
	public record Opinion(int memories, double halfLifeDays, int strongThreshold, double strongMultiplier, double townInfluence, int mergeDays,
		int witnessRadius, int maxWitnesses, double familyShare, int hearsayPerDay, double hearsayShare) {
		public static final Opinion DEFAULTS = new Opinion(12, 20, 40, 4, 0.25, 1, 24, 12, 1.0, 3, 0.3);
		public static final Codec<Opinion> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 64).optionalFieldOf("memories", DEFAULTS.memories).forGetter(Opinion::memories),
			Codec.doubleRange(0.5, 3650).optionalFieldOf("half_life_days", DEFAULTS.halfLifeDays).forGetter(Opinion::halfLifeDays),
			Codec.intRange(1, 100).optionalFieldOf("strong_threshold", DEFAULTS.strongThreshold).forGetter(Opinion::strongThreshold),
			Codec.doubleRange(1, 100).optionalFieldOf("strong_multiplier", DEFAULTS.strongMultiplier).forGetter(Opinion::strongMultiplier),
			Codec.doubleRange(0, 1).optionalFieldOf("town_influence", DEFAULTS.townInfluence).forGetter(Opinion::townInfluence),
			Codec.intRange(0, 30).optionalFieldOf("merge_days", DEFAULTS.mergeDays).forGetter(Opinion::mergeDays),
			Codec.intRange(1, 96).optionalFieldOf("witness_radius", DEFAULTS.witnessRadius).forGetter(Opinion::witnessRadius),
			Codec.intRange(0, 64).optionalFieldOf("max_witnesses", DEFAULTS.maxWitnesses).forGetter(Opinion::maxWitnesses),
			Codec.doubleRange(0, 2).optionalFieldOf("family_share", DEFAULTS.familyShare).forGetter(Opinion::familyShare),
			Codec.intRange(0, 64).optionalFieldOf("hearsay_per_day", DEFAULTS.hearsayPerDay).forGetter(Opinion::hearsayPerDay),
			Codec.doubleRange(0, 1).optionalFieldOf("hearsay_share", DEFAULTS.hearsayShare).forGetter(Opinion::hearsayShare)
		).apply(i, Opinion::new));
	}

	/**
	 * News travelling between towns.
	 *
	 * @param enabled           whether deeds travel at all
	 * @param threshold         smallest deed (|weight| after the table's gossip factor) that travels
	 * @param radius            blocks beyond which news has faded to nothing
	 * @param blocksPerDay      how far news travels along the roads in a day
	 * @param minDelayDays      the soonest news arrives anywhere
	 * @param linkDistance      the longest road between two neighbouring towns news can take
	 * @param neighbours        roads per town in the news network (to its nearest neighbours)
	 * @param hopFade           news fades this much at each town it passes through
	 * @param standingFactor    how much of the arriving news moves the town's standing
	 * @param maxQueue          news on the road at once (the faintest is dropped when full)
	 * @param heldDays          days news waits for a town that is not yet settled (adopted) before it is forgotten
	 * @param tellPlayer        tell a player when word of them reaches a town
	 */
	public record Gossip(boolean enabled, int threshold, int radius, int blocksPerDay, int minDelayDays, int linkDistance, int neighbours, double hopFade,
		double standingFactor, int maxQueue, int heldDays, boolean tellPlayer) {
		public static final Gossip DEFAULTS = new Gossip(true, 20, 6000, 1500, 1, 3000, 4, 0.85, 0.5, 2048, 60, true);
		public static final Codec<Gossip> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Gossip::enabled),
			Codec.intRange(1, 100).optionalFieldOf("threshold", DEFAULTS.threshold).forGetter(Gossip::threshold),
			Codec.intRange(100, 20000).optionalFieldOf("radius", DEFAULTS.radius).forGetter(Gossip::radius),
			Codec.intRange(50, 100000).optionalFieldOf("blocks_per_day", DEFAULTS.blocksPerDay).forGetter(Gossip::blocksPerDay),
			Codec.intRange(0, 30).optionalFieldOf("min_delay_days", DEFAULTS.minDelayDays).forGetter(Gossip::minDelayDays),
			Codec.intRange(100, 10000).optionalFieldOf("link_distance", DEFAULTS.linkDistance).forGetter(Gossip::linkDistance),
			Codec.intRange(1, 12).optionalFieldOf("neighbours", DEFAULTS.neighbours).forGetter(Gossip::neighbours),
			Codec.doubleRange(0, 1).optionalFieldOf("hop_fade", DEFAULTS.hopFade).forGetter(Gossip::hopFade),
			Codec.doubleRange(0, 2).optionalFieldOf("standing_factor", DEFAULTS.standingFactor).forGetter(Gossip::standingFactor),
			Codec.intRange(16, 100000).optionalFieldOf("max_queue", DEFAULTS.maxQueue).forGetter(Gossip::maxQueue),
			Codec.intRange(0, 3650).optionalFieldOf("held_days", DEFAULTS.heldDays).forGetter(Gossip::heldDays),
			Codec.BOOL.optionalFieldOf("notify", DEFAULTS.tellPlayer).forGetter(Gossip::tellPlayer)
		).apply(i, Gossip::new));
	}

	/**
	 * Regional fame: how known a player is around a place.
	 *
	 * @param threshold     smallest deed (|weight| after the table's fame factor) that makes a player known
	 * @param radius        blocks over which a deed's fame fades to nothing
	 * @param halfLifeDays  days for fame to halve
	 * @param sources       remembered places of fame per player (nearby deeds merge)
	 * @param scale         fame points per point of deed weight at the place of the deed
	 */
	public record Fame(int threshold, int radius, double halfLifeDays, int sources, double scale) {
		public static final Fame DEFAULTS = new Fame(15, 2000, 60, 32, 0.6);
		public static final Codec<Fame> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 100).optionalFieldOf("threshold", DEFAULTS.threshold).forGetter(Fame::threshold),
			Codec.intRange(100, 20000).optionalFieldOf("radius", DEFAULTS.radius).forGetter(Fame::radius),
			Codec.doubleRange(1, 36500).optionalFieldOf("half_life_days", DEFAULTS.halfLifeDays).forGetter(Fame::halfLifeDays),
			Codec.intRange(1, 256).optionalFieldOf("sources", DEFAULTS.sources).forGetter(Fame::sources),
			Codec.doubleRange(0, 10).optionalFieldOf("scale", DEFAULTS.scale).forGetter(Fame::scale)
		).apply(i, Fame::new));
	}

	/**
	 * Charisma and menace (docs/LIVING_WORLD.md §3, "Appearance").
	 *
	 * @param enabled             when off, charisma is always 0 and menace only counts armour and weapons
	 * @param dirtyAfterDays      days without a wash or a swim until a player is filthy
	 * @param bloodMinutes        minutes blood from a fight shows (a swim washes it off sooner)
	 * @param bathBonusHours      hours a proper bath (in a bathhouse) keeps a player freshly bathed
	 * @param violenceHalfLifeDays days for a reputation for violence to halve
	 */
	public record Appearance(boolean enabled, double dirtyAfterDays, int bloodMinutes, int bathBonusHours, double violenceHalfLifeDays) {
		public static final Appearance DEFAULTS = new Appearance(true, 3, 5, 24, 30);
		public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Appearance::enabled),
			Codec.doubleRange(0.1, 365).optionalFieldOf("dirty_after_days", DEFAULTS.dirtyAfterDays).forGetter(Appearance::dirtyAfterDays),
			Codec.intRange(0, 240).optionalFieldOf("blood_minutes", DEFAULTS.bloodMinutes).forGetter(Appearance::bloodMinutes),
			Codec.intRange(0, 240).optionalFieldOf("bath_bonus_hours", DEFAULTS.bathBonusHours).forGetter(Appearance::bathBonusHours),
			Codec.doubleRange(1, 3650).optionalFieldOf("violence_half_life_days", DEFAULTS.violenceHalfLifeDays).forGetter(Appearance::violenceHalfLifeDays)
		).apply(i, Appearance::new));
	}

	/**
	 * Speech checks.
	 *
	 * @param dialogue        offer the standing module's own conversation options (flattery, amends, gossip)
	 * @param minChance       the worst odds any check has
	 * @param maxChance       the best odds any check has
	 * @param bribeMultiplier scales what folk expect to be paid
	 * @param consequences    a check leaves a memory (a failed threat sours them, a successful flattery warms them)
	 */
	public record Speech(boolean dialogue, double minChance, double maxChance, double bribeMultiplier, boolean consequences) {
		public static final Speech DEFAULTS = new Speech(true, 0.05, 0.95, 1.0, true);
		public static final Codec<Speech> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("dialogue", DEFAULTS.dialogue).forGetter(Speech::dialogue),
			Codec.doubleRange(0, 1).optionalFieldOf("min_chance", DEFAULTS.minChance).forGetter(Speech::minChance),
			Codec.doubleRange(0, 1).optionalFieldOf("max_chance", DEFAULTS.maxChance).forGetter(Speech::maxChance),
			Codec.doubleRange(0, 100).optionalFieldOf("bribe_multiplier", DEFAULTS.bribeMultiplier).forGetter(Speech::bribeMultiplier),
			Codec.BOOL.optionalFieldOf("consequences", DEFAULTS.consequences).forGetter(Speech::consequences)
		).apply(i, Speech::new));
	}

	/**
	 * Companies: sworn bands of players who share part of each other's standing.
	 *
	 * @param enabled    whether players may form companies (a server toggle)
	 * @param share      share of a member's standing change that every other member also gets
	 * @param maxMembers largest company
	 * @param inviteOnly joining needs an invitation from a member
	 */
	public record Companies(boolean enabled, double share, int maxMembers, boolean inviteOnly) {
		public static final Companies DEFAULTS = new Companies(true, 0.25, 8, true);
		public static final Codec<Companies> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Companies::enabled),
			Codec.doubleRange(0, 1).optionalFieldOf("share", DEFAULTS.share).forGetter(Companies::share),
			Codec.intRange(2, 64).optionalFieldOf("max_members", DEFAULTS.maxMembers).forGetter(Companies::maxMembers),
			Codec.BOOL.optionalFieldOf("invite_only", DEFAULTS.inviteOnly).forGetter(Companies::inviteOnly)
		).apply(i, Companies::new));
	}
}
