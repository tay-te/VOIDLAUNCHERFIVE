package dev.voidmc.hearth.sim.roads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/roads.json} (docs/LIVING_WORLD_2.md §1). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record RoadsConfig(Roads roads, Traffic traffic, Parties parties, Bandits bandits) {
	public static final RoadsConfig DEFAULTS = new RoadsConfig(Roads.DEFAULTS, Traffic.DEFAULTS, Parties.DEFAULTS, Bandits.DEFAULTS);

	public static final Codec<RoadsConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Roads.CODEC.optionalFieldOf("roads", Roads.DEFAULTS).forGetter(RoadsConfig::roads),
		Traffic.CODEC.optionalFieldOf("traffic", Traffic.DEFAULTS).forGetter(RoadsConfig::traffic),
		Parties.CODEC.optionalFieldOf("parties", Parties.DEFAULTS).forGetter(RoadsConfig::parties),
		Bandits.CODEC.optionalFieldOf("bandits", Bandits.DEFAULTS).forGetter(RoadsConfig::bandits)
	).apply(i, RoadsConfig::new));

	/**
	 * @param enabled             roads between settlements are planned, built and travelled
	 * @param routeCache          historic routes kept in memory
	 * @param labourShare         share of a town's idle builder labour given to its roads each day
	 * @param liveRoutesPerSecond live route searches started a second (off the server thread)
	 * @param maxLength           the longest road between neighbouring places
	 * @param writeBudgetMs       server time a tick the roads may spend writing into chunks (a third of the applier's)
	 * @param writeMaxBlocks      blocks a tick the roads may write
	 * @param liveHistoric        historic roads are written live into land generated before they were (existing worlds)
	 */
	public record Roads(boolean enabled, int routeCache, double labourShare, int liveRoutesPerSecond, int maxLength, double writeBudgetMs,
		int writeMaxBlocks, boolean liveHistoric) {
		public static final Roads DEFAULTS = new Roads(true, 512, 0.1, 2, 2400, 1.0, 2000, true);
		public static final Codec<Roads> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Roads::enabled),
			Codec.intRange(0, 65536).optionalFieldOf("route_cache", DEFAULTS.routeCache).forGetter(Roads::routeCache),
			Codec.doubleRange(0, 1).optionalFieldOf("labour_share", DEFAULTS.labourShare).forGetter(Roads::labourShare),
			Codec.intRange(0, 20).optionalFieldOf("live_routes_per_second", DEFAULTS.liveRoutesPerSecond).forGetter(Roads::liveRoutesPerSecond),
			Codec.intRange(400, 8000).optionalFieldOf("max_length", DEFAULTS.maxLength).forGetter(Roads::maxLength),
			Codec.doubleRange(0, 20).optionalFieldOf("write_budget_ms", DEFAULTS.writeBudgetMs).forGetter(Roads::writeBudgetMs),
			Codec.intRange(0, 100000).optionalFieldOf("write_max_blocks", DEFAULTS.writeMaxBlocks).forGetter(Roads::writeMaxBlocks),
			Codec.BOOL.optionalFieldOf("live_historic", DEFAULTS.liveHistoric).forGetter(Roads::liveHistoric)
		).apply(i, Roads::new));
	}

	/**
	 * @param maxJourneys journeys on the roads world-wide; when full, the least important kinds are not sent
	 * @param patrols     towns with this many guards or more send patrols
	 * @param pilgrims    pilgrims are sent on feast days
	 * @param marketGoers market goers walk to a neighbour's market within this many blocks
	 */
	public record Traffic(int maxJourneys, int patrols, boolean pilgrims, int marketGoers) {
		public static final Traffic DEFAULTS = new Traffic(512, 3, true, 900);
		public static final Codec<Traffic> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 8192).optionalFieldOf("max_journeys", DEFAULTS.maxJourneys).forGetter(Traffic::maxJourneys),
			Codec.intRange(1, 64).optionalFieldOf("patrol_guards", DEFAULTS.patrols).forGetter(Traffic::patrols),
			Codec.BOOL.optionalFieldOf("pilgrims", DEFAULTS.pilgrims).forGetter(Traffic::pilgrims),
			Codec.intRange(0, 4000).optionalFieldOf("market_goers", DEFAULTS.marketGoers).forGetter(Traffic::marketGoers)
		).apply(i, Traffic::new));
	}

	/**
	 * @param maxParties    travelling parties embodied server-wide
	 * @param maxEntities   traveller entities server-wide (folk, draught animals, carts)
	 * @param spawnDistance a party is embodied within this many blocks of a player
	 * @param foldDistance  and folded away beyond this many
	 */
	public record Parties(int maxParties, int maxEntities, int spawnDistance, int foldDistance) {
		public static final Parties DEFAULTS = new Parties(16, 120, 96, 112);
		public static final Codec<Parties> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 256).optionalFieldOf("max_parties", DEFAULTS.maxParties).forGetter(Parties::maxParties),
			Codec.intRange(0, 2048).optionalFieldOf("max_entities", DEFAULTS.maxEntities).forGetter(Parties::maxEntities),
			Codec.intRange(16, 256).optionalFieldOf("spawn_distance", DEFAULTS.spawnDistance).forGetter(Parties::spawnDistance),
			Codec.intRange(24, 320).optionalFieldOf("fold_distance", DEFAULTS.foldDistance).forGetter(Parties::foldDistance)
		).apply(i, Parties::new));
	}

	/**
	 * @param maxBands   bandit bands world-wide
	 * @param killChance chance a waylaid journey loses a traveller for good (permadeath); 0 never
	 * @param mayKill    bandits may kill travellers at all (section 13: yes, rarely)
	 * @param toll       share of the coin a player carries a band demands
	 * @param maxToll    the most a band demands, in bits (two crowns)
	 */
	public record Bandits(int maxBands, double killChance, boolean mayKill, double toll, long maxToll) {
		public static final Bandits DEFAULTS = new Bandits(32, 0.05, true, 0.25, 200);
		public static final Codec<Bandits> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 512).optionalFieldOf("max_bands", DEFAULTS.maxBands).forGetter(Bandits::maxBands),
			Codec.doubleRange(0, 1).optionalFieldOf("kill_chance", DEFAULTS.killChance).forGetter(Bandits::killChance),
			Codec.BOOL.optionalFieldOf("may_kill", DEFAULTS.mayKill).forGetter(Bandits::mayKill),
			Codec.doubleRange(0, 1).optionalFieldOf("toll", DEFAULTS.toll).forGetter(Bandits::toll),
			Codec.LONG.optionalFieldOf("max_toll", DEFAULTS.maxToll).forGetter(Bandits::maxToll)
		).apply(i, Bandits::new));
	}
}
