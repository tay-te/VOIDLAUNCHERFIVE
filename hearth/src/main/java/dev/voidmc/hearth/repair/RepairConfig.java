package dev.voidmc.hearth.repair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/repair.json}. Every field is optional, so an old file keeps working when fields are added. See
 * docs/REPAIRS.md for what each switch does.
 */
public record RepairConfig(Tracking tracking, Queue queue, Abstract abstractRepair, Builders builders, Inspection inspection, Fire fire,
	Law law, Owners owners) {
	public static final RepairConfig DEFAULTS = new RepairConfig(Tracking.DEFAULTS, Queue.DEFAULTS, Abstract.DEFAULTS, Builders.DEFAULTS,
		Inspection.DEFAULTS, Fire.DEFAULTS, Law.DEFAULTS, Owners.DEFAULTS);

	public static final Codec<RepairConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Tracking.CODEC.optionalFieldOf("tracking", Tracking.DEFAULTS).forGetter(RepairConfig::tracking),
		Queue.CODEC.optionalFieldOf("queue", Queue.DEFAULTS).forGetter(RepairConfig::queue),
		Abstract.CODEC.optionalFieldOf("abstract", Abstract.DEFAULTS).forGetter(RepairConfig::abstractRepair),
		Builders.CODEC.optionalFieldOf("builders", Builders.DEFAULTS).forGetter(RepairConfig::builders),
		Inspection.CODEC.optionalFieldOf("inspection", Inspection.DEFAULTS).forGetter(RepairConfig::inspection),
		Fire.CODEC.optionalFieldOf("fire", Fire.DEFAULTS).forGetter(RepairConfig::fire),
		Law.CODEC.optionalFieldOf("law", Law.DEFAULTS).forGetter(RepairConfig::law),
		Owners.CODEC.optionalFieldOf("owners", Owners.DEFAULTS).forGetter(RepairConfig::owners)
	).apply(i, RepairConfig::new));

	/**
	 * What counts as damage and how it is noticed.
	 *
	 * @param enabled         master switch: nothing is tracked (or repaired) when off
	 * @param playerBreaks    blocks players break
	 * @param explosions      creepers, TNT, beds in the nether, anything that explodes
	 * @param fire            blocks fire burns away
	 * @param otherBreaks     every other {@code destroyBlock}: withers, ravagers, attached blocks (torches, lanterns, doors)
	 *                        popping off when what held them went
	 * @param litter          blocks players leave in doorways and on streets are cleared
	 * @param indexMaxEntries how many blueprint cells the position index keeps (bounded LRU of plot sections)
	 * @param gridTtlTicks    how long a settlement's plot grid is trusted before it is rebuilt (new plots appear in it then)
	 */
	public record Tracking(boolean enabled, boolean playerBreaks, boolean explosions, boolean fire, boolean otherBreaks, boolean litter,
		int indexMaxEntries, int gridTtlTicks) {
		public static final Tracking DEFAULTS = new Tracking(true, true, true, true, true, true, 400_000, 600);

		public static final Codec<Tracking> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Tracking::enabled),
			Codec.BOOL.optionalFieldOf("player_breaks", DEFAULTS.playerBreaks).forGetter(Tracking::playerBreaks),
			Codec.BOOL.optionalFieldOf("explosions", DEFAULTS.explosions).forGetter(Tracking::explosions),
			Codec.BOOL.optionalFieldOf("fire", DEFAULTS.fire).forGetter(Tracking::fire),
			Codec.BOOL.optionalFieldOf("other_breaks", DEFAULTS.otherBreaks).forGetter(Tracking::otherBreaks),
			Codec.BOOL.optionalFieldOf("litter", DEFAULTS.litter).forGetter(Tracking::litter),
			Codec.INT.optionalFieldOf("index_max_entries", DEFAULTS.indexMaxEntries).forGetter(Tracking::indexMaxEntries),
			Codec.INT.optionalFieldOf("grid_ttl_ticks", DEFAULTS.gridTtlTicks).forGetter(Tracking::gridTtlTicks)
		).apply(i, Tracking::new));
	}

	/**
	 * The repair queue of each settlement.
	 *
	 * @param maxEntries  damaged blocks remembered per settlement (a bigger crater is mended as far as it is remembered)
	 * @param claimTicks  how long a builder's claim on a block holds before it returns to the queue
	 * @param chargeGoods repairs take their goods from the stockpile, as construction does
	 * @param deferTicks  a block that cannot be mended yet (no goods, nothing to hold it up) is left this long
	 */
	public record Queue(int maxEntries, int claimTicks, boolean chargeGoods, int deferTicks) {
		public static final Queue DEFAULTS = new Queue(4096, 1200, true, 400);

		public static final Codec<Queue> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("max_entries", DEFAULTS.maxEntries).forGetter(Queue::maxEntries),
			Codec.INT.optionalFieldOf("claim_ticks", DEFAULTS.claimTicks).forGetter(Queue::claimTicks),
			Codec.BOOL.optionalFieldOf("charge_goods", DEFAULTS.chargeGoods).forGetter(Queue::chargeGoods),
			Codec.INT.optionalFieldOf("defer_ticks", DEFAULTS.deferTicks).forGetter(Queue::deferTicks)
		).apply(i, Queue::new));
	}

	/**
	 * Repairs while builders are records (nobody near, or builders not walking about as entities).
	 *
	 * @param enabled              abstract repair runs at all
	 * @param blocksPerBuilderHour blocks one builder mends in a sim hour (an embodied builder places about 33 blocks an
	 *                             hour of construction; mending means finding the damage, so half of that)
	 * @param intervalTicks        how often settlements with damage accrue repair labour
	 * @param maxCatchUpDays       labour accrued after a long absence is capped at this many days
	 * @param applyMaxBlocks       mended blocks written into loaded chunks per tick
	 * @param applyBudgetMs        time per tick for writing them
	 */
	public record Abstract(boolean enabled, double blocksPerBuilderHour, int intervalTicks, int maxCatchUpDays, int applyMaxBlocks,
		double applyBudgetMs) {
		public static final Abstract DEFAULTS = new Abstract(true, 16.0, 200, 30, 256, 1.0);

		public static final Codec<Abstract> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Abstract::enabled),
			Codec.DOUBLE.optionalFieldOf("blocks_per_builder_hour", DEFAULTS.blocksPerBuilderHour).forGetter(Abstract::blocksPerBuilderHour),
			Codec.INT.optionalFieldOf("interval_ticks", DEFAULTS.intervalTicks).forGetter(Abstract::intervalTicks),
			Codec.INT.optionalFieldOf("max_catch_up_days", DEFAULTS.maxCatchUpDays).forGetter(Abstract::maxCatchUpDays),
			Codec.INT.optionalFieldOf("apply_max_blocks", DEFAULTS.applyMaxBlocks).forGetter(Abstract::applyMaxBlocks),
			Codec.DOUBLE.optionalFieldOf("apply_budget_ms", DEFAULTS.applyBudgetMs).forGetter(Abstract::applyBudgetMs)
		).apply(i, Abstract::new));
	}

	/**
	 * Embodied builders who mend.
	 *
	 * @param enabled            builders mend at all (otherwise every repair is abstract)
	 * @param priority           their behaviour's priority (100–199: work that interrupts the routine)
	 * @param workStart          the time of day (0–24000) mending may begin
	 * @param workEnd            ... and end
	 * @param radius             a builder goes to damage within this many blocks
	 * @param stepTicks          ticks between placements, as construction
	 * @param scaffolding        high repairs are reached on scaffolding
	 * @param chainRadius        after a block, a builder takes the next one within this many blocks without stopping
	 * @param speakCooldownTicks a builder comments on the damage at most this often
	 */
	public record Builders(boolean enabled, int priority, int workStart, int workEnd, int radius, int stepTicks, boolean scaffolding,
		int chainRadius, int speakCooldownTicks) {
		public static final Builders DEFAULTS = new Builders(true, 150, 1000, 12500, 96, 25, true, 24, 1200);

		public static final Codec<Builders> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Builders::enabled),
			Codec.INT.optionalFieldOf("priority", DEFAULTS.priority).forGetter(Builders::priority),
			Codec.INT.optionalFieldOf("work_start", DEFAULTS.workStart).forGetter(Builders::workStart),
			Codec.INT.optionalFieldOf("work_end", DEFAULTS.workEnd).forGetter(Builders::workEnd),
			Codec.INT.optionalFieldOf("radius", DEFAULTS.radius).forGetter(Builders::radius),
			Codec.INT.optionalFieldOf("step_ticks", DEFAULTS.stepTicks).forGetter(Builders::stepTicks),
			Codec.BOOL.optionalFieldOf("scaffolding", DEFAULTS.scaffolding).forGetter(Builders::scaffolding),
			Codec.INT.optionalFieldOf("chain_radius", DEFAULTS.chainRadius).forGetter(Builders::chainRadius),
			Codec.INT.optionalFieldOf("speak_cooldown_ticks", DEFAULTS.speakCooldownTicks).forGetter(Builders::speakCooldownTicks)
		).apply(i, Builders::new));
	}

	/**
	 * Builders' inspection rounds, which catch what no event saw.
	 *
	 * @param enabled          inspections happen
	 * @param priority         the behaviour's priority (below mending)
	 * @param intervalTicks    one round per settlement this often, by one builder
	 * @param positionsPerTick blueprint cells compared with the world per tick of a round (20 = 400 a second)
	 * @param maxTicks         a round ends after this long whatever it has covered (the rest waits for the next round)
	 */
	public record Inspection(boolean enabled, int priority, int intervalTicks, int positionsPerTick, int maxTicks) {
		public static final Inspection DEFAULTS = new Inspection(true, 110, 2400, 20, 2400);

		public static final Codec<Inspection> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Inspection::enabled),
			Codec.INT.optionalFieldOf("priority", DEFAULTS.priority).forGetter(Inspection::priority),
			Codec.INT.optionalFieldOf("interval_ticks", DEFAULTS.intervalTicks).forGetter(Inspection::intervalTicks),
			Codec.INT.optionalFieldOf("positions_per_tick", DEFAULTS.positionsPerTick).forGetter(Inspection::positionsPerTick),
			Codec.INT.optionalFieldOf("max_ticks", DEFAULTS.maxTicks).forGetter(Inspection::maxTicks)
		).apply(i, Inspection::new));
	}

	/**
	 * Fires in town: the bucket brigade and what comes after.
	 *
	 * @param enabled         fires are followed as events (episodes)
	 * @param brigade         folk carry water to the flames
	 * @param priority        the brigade's priority (200–299: reactions)
	 * @param radius          folk within this many blocks of a fire join the brigade
	 * @param maxFolk         at most this many folk per fire
	 * @param douseRadius     fire within this many blocks of where a bucket lands goes out
	 * @param waterSearch     how far from the fire folk look for water
	 * @param quietTicks      a fire is out once no flame has been seen for this long
	 * @param brigadeDeedMax  the most standing a player earns for fighting one fire
	 * @param rebuildDeedMax  the most standing a player earns per settlement per round for putting blocks back
	 */
	public record Fire(boolean enabled, boolean brigade, int priority, int radius, int maxFolk, int douseRadius, int waterSearch,
		int quietTicks, int brigadeDeedMax, int rebuildDeedMax) {
		public static final Fire DEFAULTS = new Fire(true, true, 250, 48, 12, 2, 24, 100, 15, 10);

		public static final Codec<Fire> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Fire::enabled),
			Codec.BOOL.optionalFieldOf("brigade", DEFAULTS.brigade).forGetter(Fire::brigade),
			Codec.INT.optionalFieldOf("priority", DEFAULTS.priority).forGetter(Fire::priority),
			Codec.INT.optionalFieldOf("radius", DEFAULTS.radius).forGetter(Fire::radius),
			Codec.INT.optionalFieldOf("max_folk", DEFAULTS.maxFolk).forGetter(Fire::maxFolk),
			Codec.INT.optionalFieldOf("douse_radius", DEFAULTS.douseRadius).forGetter(Fire::douseRadius),
			Codec.INT.optionalFieldOf("water_search", DEFAULTS.waterSearch).forGetter(Fire::waterSearch),
			Codec.INT.optionalFieldOf("quiet_ticks", DEFAULTS.quietTicks).forGetter(Fire::quietTicks),
			Codec.INT.optionalFieldOf("brigade_deed_max", DEFAULTS.brigadeDeedMax).forGetter(Fire::brigadeDeedMax),
			Codec.INT.optionalFieldOf("rebuild_deed_max", DEFAULTS.rebuildDeedMax).forGetter(Fire::rebuildDeedMax)
		).apply(i, Fire::new));
	}

	/**
	 * Vandalism and the law.
	 *
	 * @param reportVandalism   players' damage is reported to the law module (which decides what it is worth to it)
	 * @param witnessRadius     folk within this many blocks who can see the player witness it
	 * @param windowTicks       a spree within this many ticks is one crime (its worth summed)
	 * @param reportOnDiscovery damage nobody saw is reported (unwitnessed: a suspicion) when a builder finds it
	 * @param exemptCreative    creative and spectator players' damage is mended but never reported
	 * @param deedPerBlock      without a law module, standing lost per block a witnessed vandal breaks (negative)
	 */
	public record Law(boolean reportVandalism, int witnessRadius, int windowTicks, boolean reportOnDiscovery, boolean exemptCreative,
		int deedPerBlock) {
		public static final Law DEFAULTS = new Law(true, 24, 100, true, true, -2);

		public static final Codec<Law> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("report_vandalism", DEFAULTS.reportVandalism).forGetter(Law::reportVandalism),
			Codec.INT.optionalFieldOf("witness_radius", DEFAULTS.witnessRadius).forGetter(Law::witnessRadius),
			Codec.INT.optionalFieldOf("window_ticks", DEFAULTS.windowTicks).forGetter(Law::windowTicks),
			Codec.BOOL.optionalFieldOf("report_on_discovery", DEFAULTS.reportOnDiscovery).forGetter(Law::reportOnDiscovery),
			Codec.BOOL.optionalFieldOf("exempt_creative", DEFAULTS.exemptCreative).forGetter(Law::exemptCreative),
			Codec.INT.optionalFieldOf("deed_per_block", DEFAULTS.deedPerBlock).forGetter(Law::deedPerBlock)
		).apply(i, Law::new));
	}

	/**
	 * Property players own (deeded plots).
	 *
	 * @param ownerPays a deeded house is mended only once its owner pays (otherwise the town mends it for free)
	 * @param markup    the owner pays the goods' market price times this
	 * @param labourFee bits per block for the builders' time
	 */
	public record Owners(boolean ownerPays, double markup, int labourFee) {
		public static final Owners DEFAULTS = new Owners(true, 1.25, 2);

		public static final Codec<Owners> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("owner_pays", DEFAULTS.ownerPays).forGetter(Owners::ownerPays),
			Codec.DOUBLE.optionalFieldOf("markup", DEFAULTS.markup).forGetter(Owners::markup),
			Codec.INT.optionalFieldOf("labour_fee", DEFAULTS.labourFee).forGetter(Owners::labourFee)
		).apply(i, Owners::new));
	}
}
