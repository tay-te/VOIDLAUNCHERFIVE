package dev.voidmc.hearth.npc.work.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/fieldwork.json} (docs/LIVING_WORLD_2.md §6). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record FieldWorkConfig(Sites sites, Flocks flocks, Boats boats, Displays displays, Clamps clamps, Mines mines, Carts carts) {
	public static final FieldWorkConfig DEFAULTS = new FieldWorkConfig(Sites.DEFAULTS, Flocks.DEFAULTS, Boats.DEFAULTS, Displays.DEFAULTS,
		Clamps.DEFAULTS, Mines.DEFAULTS, Carts.DEFAULTS);

	public static final Codec<FieldWorkConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Sites.CODEC.optionalFieldOf("sites", Sites.DEFAULTS).forGetter(FieldWorkConfig::sites),
		Flocks.CODEC.optionalFieldOf("flocks", Flocks.DEFAULTS).forGetter(FieldWorkConfig::flocks),
		Boats.CODEC.optionalFieldOf("boats", Boats.DEFAULTS).forGetter(FieldWorkConfig::boats),
		Displays.CODEC.optionalFieldOf("displays", Displays.DEFAULTS).forGetter(FieldWorkConfig::displays),
		Clamps.CODEC.optionalFieldOf("clamps", Clamps.DEFAULTS).forGetter(FieldWorkConfig::clamps),
		Mines.CODEC.optionalFieldOf("mines", Mines.DEFAULTS).forGetter(FieldWorkConfig::mines),
		Carts.CODEC.optionalFieldOf("carts", Carts.DEFAULTS).forGetter(FieldWorkConfig::carts)
	).apply(i, FieldWorkConfig::new));

	public FieldWorkConfig withSites(Sites s) {
		return new FieldWorkConfig(s, this.flocks, this.boats, this.displays, this.clamps, this.mines, this.carts);
	}

	public FieldWorkConfig withFlocks(Flocks f) {
		return new FieldWorkConfig(this.sites, f, this.boats, this.displays, this.clamps, this.mines, this.carts);
	}

	public FieldWorkConfig withCarts(Carts c) {
		return new FieldWorkConfig(this.sites, this.flocks, this.boats, this.displays, this.clamps, this.mines, c);
	}

	/**
	 * @param enabled         remote work sites (pastures, the Great Mine) are worked and embodied
	 * @param embodyDistance  a remote site is embodied within this many blocks of a player
	 * @param passTicks       how often remote sites round each player are looked at
	 * @param pastureDistance how far from its pen a flock is driven to graze, at most (blocks)
	 * @param commuteFrom     a worker whose site lies at least this far from home walks out to it on the track at the start
	 *                        and end of the day (and is placed on the way while a record)
	 * @param walkSpeed       blocks a tick a drove and a commuter make, for the time the way takes (a folk walks about 0.11)
	 * @param maxPerTown      remote sites a town keeps, nearest first
	 */
	public record Sites(boolean enabled, int embodyDistance, int passTicks, int pastureDistance, int commuteFrom, double walkSpeed, int maxPerTown) {
		public static final Sites DEFAULTS = new Sites(true, 96, 40, 300, 64, 0.1, 12);
		public static final Codec<Sites> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Sites::enabled),
			Codec.intRange(16, 256).optionalFieldOf("embody_distance", DEFAULTS.embodyDistance).forGetter(Sites::embodyDistance),
			Codec.intRange(1, 1200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Sites::passTicks),
			Codec.intRange(32, 1024).optionalFieldOf("pasture_distance", DEFAULTS.pastureDistance).forGetter(Sites::pastureDistance),
			Codec.intRange(8, 512).optionalFieldOf("commute_from", DEFAULTS.commuteFrom).forGetter(Sites::commuteFrom),
			Codec.doubleRange(0.01, 1.0).optionalFieldOf("walk_speed", DEFAULTS.walkSpeed).forGetter(Sites::walkSpeed),
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Sites::maxPerTown)
		).apply(i, Sites::new));
	}

	/**
	 * @param herdPerHerder  animals a herder keeps on a pasture
	 * @param maxPerPasture  flock animals and dogs embodied on one pasture
	 * @param maxPerTown     flock animals and dogs embodied for one town
	 * @param maxPerServer   flock animals and dogs embodied server-wide
	 * @param playerDistance a flock is embodied only within this many blocks of a player (and folded past it plus 16)
	 * @param healPerDay     animals of a flock lost (killed, stolen) that are made good each sim day
	 */
	public record Flocks(int herdPerHerder, int maxPerPasture, int maxPerTown, int maxPerServer, int playerDistance, int healPerDay) {
		public static final Flocks DEFAULTS = new Flocks(8, 8, 24, 96, 64, 1);
		public static final Codec<Flocks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("herd_per_herder", DEFAULTS.herdPerHerder).forGetter(Flocks::herdPerHerder),
			Codec.intRange(0, 64).optionalFieldOf("max_per_pasture", DEFAULTS.maxPerPasture).forGetter(Flocks::maxPerPasture),
			Codec.intRange(0, 256).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Flocks::maxPerTown),
			Codec.intRange(0, 2048).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Flocks::maxPerServer),
			Codec.intRange(16, 160).optionalFieldOf("player_distance", DEFAULTS.playerDistance).forGetter(Flocks::playerDistance),
			Codec.intRange(0, 64).optionalFieldOf("heal_per_day", DEFAULTS.healPerDay).forGetter(Flocks::healPerDay)
		).apply(i, Flocks::new));
	}

	/**
	 * @param maxPerTown   fishing boats embodied for one town
	 * @param maxPerServer fishing boats embodied server-wide
	 * @param perDock      boats moored at one dock, at most (1 to 3 by the dock's water)
	 * @param reach        how far out (blocks) a fisher rows, at most
	 */
	public record Boats(int maxPerTown, int maxPerServer, int perDock, int reach) {
		public static final Boats DEFAULTS = new Boats(6, 24, 3, 60);
		public static final Codec<Boats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Boats::maxPerTown),
			Codec.intRange(0, 512).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Boats::maxPerServer),
			Codec.intRange(0, 8).optionalFieldOf("per_dock", DEFAULTS.perDock).forGetter(Boats::perDock),
			Codec.intRange(8, 160).optionalFieldOf("reach", DEFAULTS.reach).forGetter(Boats::reach)
		).apply(i, Boats::new));
	}

	/**
	 * @param maxPerStall  item displays (wares) on one stall
	 * @param maxPerTown   item displays in one town
	 * @param maxPerServer item displays server-wide
	 */
	public record Displays(int maxPerStall, int maxPerTown, int maxPerServer) {
		public static final Displays DEFAULTS = new Displays(6, 60, 1500);
		public static final Codec<Displays> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("max_per_stall", DEFAULTS.maxPerStall).forGetter(Displays::maxPerStall),
			Codec.intRange(0, 1024).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Displays::maxPerTown),
			Codec.intRange(0, 65536).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Displays::maxPerServer)
		).apply(i, Displays::new));
	}

	/**
	 * @param cycleDays sim days of a clamp's burn: one building the stack, the middle ones smouldering, the last raking
	 * @param smoke     the smouldering clamp breathes a column of smoke (seen from the road)
	 */
	public record Clamps(int cycleDays, boolean smoke) {
		public static final Clamps DEFAULTS = new Clamps(4, true);
		public static final Codec<Clamps> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(3, 30).optionalFieldOf("cycle_days", DEFAULTS.cycleDays).forGetter(Clamps::cycleDays),
			Codec.BOOL.optionalFieldOf("smoke", DEFAULTS.smoke).forGetter(Clamps::smoke)
		).apply(i, Clamps::new));
	}

	/**
	 * @param greatMines a town whose territory reaches one of Expanse's great mines reopens it
	 * @param claim      the square (± blocks of the shaft) claimed for the living pithead, so Expanse's abandoned one
	 *                   stands down
	 */
	public record Mines(boolean greatMines, int claim) {
		public static final Mines DEFAULTS = new Mines(true, 12);
		public static final Codec<Mines> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("great_mines", DEFAULTS.greatMines).forGetter(Mines::greatMines),
			Codec.intRange(8, 32).optionalFieldOf("claim", DEFAULTS.claim).forGetter(Mines::claim)
		).apply(i, Mines::new));
	}

	/**
	 * Haulers' handcarts (lane A1's {@code hearth:cart}).
	 *
	 * @param enabled      haulers draw handcarts on longer hauls
	 * @param minHaul      a haul at least this long (blocks, source to sink) gets a cart; shorter ones are carried
	 * @param maxPerTown   handcarts out for one town
	 * @param maxPerServer handcarts out server-wide
	 */
	public record Carts(boolean enabled, int minHaul, int maxPerTown, int maxPerServer) {
		public static final Carts DEFAULTS = new Carts(true, 16, 4, 32);
		public static final Codec<Carts> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Carts::enabled),
			Codec.intRange(0, 512).optionalFieldOf("min_haul", DEFAULTS.minHaul).forGetter(Carts::minHaul),
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Carts::maxPerTown),
			Codec.intRange(0, 512).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Carts::maxPerServer)
		).apply(i, Carts::new));
	}
}
