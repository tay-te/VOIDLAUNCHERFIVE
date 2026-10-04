package dev.voidmc.hearth.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/npc.json}. Every field is optional with a default, so old files keep working as fields are
 * added.
 *
 * <pre>
 * embodiment  spawn_radius 96, despawn_radius 112   blocks from the nearest player
 *             max_per_settlement 48, max_per_server 400
 *             spawns_per_tick 4                     new entities per server tick, across the server
 *             pass_interval 10, settlements_per_pass 4   how often, and how many settlements, the manager looks at
 * ai          think_near 2, think_mid 5, think_far 12    ticks between a folk's decisions, by distance to a player
 *             near_distance 24, mid_distance 64
 *             repath_cooldown 40                    ticks before a walking folk may ask for a new path
 *             builder_step_ticks 30                 an embodied builder places about one step this often
 *             scaffolding true                      builders put up scaffolding for high work
 * threats     hostiles_target_folk true             zombies, skeletons, raiders and spiders go after folk
 *             death_messages true                   tell nearby players when a resident dies
 * talk        greetings true                        folk who know you greet you by name in passing
 *             marker_refresh 40                     ticks between quest marker updates
 *             landmark_rumours true                 folk give directions to Expanse landmarks (a structure search, cached)
 * perception  sight_range 24                        blocks a folk can see a player or a deed (FolkHandle.canSee)
 *             field_of_view 140                     degrees across their gaze; closer than near_sense they notice anyway
 *             near_sense 1.5
 *             behaviour_poll 20                     ticks between asking other modules' behaviours whether they want a folk
 * gathering   terrain_changes true                  gatherers change the land (fell trees, cut excavation steps); false =
 *                                                   they mime it and the loop runs as numbers (docs/LIVING_WORLD.md 7.8)
 *             cut_step_ticks 30                     a miner or quarrier cuts about one excavation step this often
 *             max_tree_logs 320                     trees bigger than this (the redwood giants) are never felled
 *             saplings_per_tree 2                   foresters replant this many for every tree they fell
 *             keep_clear_of_deeds 8                 blocks between any felling and a player's deeded plot
 *             shear_and_milk true                   herders shear the sheep and milk the cows of their pasture (no drops)
 *             harvest_hives true                    beekeepers take honey from full hives
 * caravans    embodied true                         caravans are met on the road near players (traders, guards, pack animals)
 *             max_active 48                         caravans remembered at once, server-wide
 *             speed 1.6                             blocks per second along the road (they arrive when the sim says so)
 *             guards 2, traders 1, animals 3        the party, at most
 * escort      teleport_distance 20                  an escorted folk left this far behind catches up out of sight
 *             lose_after 1200                       ticks with the player gone (logged off, another dimension) before the escort is lost
 *             arrive_distance 5                     blocks from the destination that count as arrived
 * </pre>
 */
public record NpcConfig(Embodiment embodiment, Ai ai, Threats threats, Talk talk, Perception perception, Gathering gathering, Caravans caravans,
	Escort escort) {
	public static final NpcConfig DEFAULTS = new NpcConfig(Embodiment.DEFAULTS, Ai.DEFAULTS, Threats.DEFAULTS, Talk.DEFAULTS, Perception.DEFAULTS,
		Gathering.DEFAULTS, Caravans.DEFAULTS, Escort.DEFAULTS);

	public static final Codec<NpcConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Embodiment.CODEC.optionalFieldOf("embodiment", Embodiment.DEFAULTS).forGetter(NpcConfig::embodiment),
		Ai.CODEC.optionalFieldOf("ai", Ai.DEFAULTS).forGetter(NpcConfig::ai),
		Threats.CODEC.optionalFieldOf("threats", Threats.DEFAULTS).forGetter(NpcConfig::threats),
		Talk.CODEC.optionalFieldOf("talk", Talk.DEFAULTS).forGetter(NpcConfig::talk),
		Perception.CODEC.optionalFieldOf("perception", Perception.DEFAULTS).forGetter(NpcConfig::perception),
		Gathering.CODEC.optionalFieldOf("gathering", Gathering.DEFAULTS).forGetter(NpcConfig::gathering),
		Caravans.CODEC.optionalFieldOf("caravans", Caravans.DEFAULTS).forGetter(NpcConfig::caravans),
		Escort.CODEC.optionalFieldOf("escort", Escort.DEFAULTS).forGetter(NpcConfig::escort)
	).apply(i, NpcConfig::new));

	/** The phase 2 settings with the phase 3 sections at their defaults (older callers, game tests). */
	public NpcConfig(Embodiment embodiment, Ai ai, Threats threats, Talk talk) {
		this(embodiment, ai, threats, talk, Perception.DEFAULTS, Gathering.DEFAULTS, Caravans.DEFAULTS, Escort.DEFAULTS);
	}

	/** A copy with other gathering settings (dev tools and game tests). */
	public NpcConfig withGathering(Gathering g) {
		return new NpcConfig(this.embodiment, this.ai, this.threats, this.talk, this.perception, g, this.caravans, this.escort);
	}

	/** A copy with other AI settings (dev tools and game tests: faster builders). */
	public NpcConfig withAi(Ai a) {
		return new NpcConfig(this.embodiment, a, this.threats, this.talk, this.perception, this.gathering, this.caravans, this.escort);
	}

	public record Perception(double sightRange, double fieldOfView, double nearSense, int behaviourPoll) {
		public static final Perception DEFAULTS = new Perception(24.0, 140.0, 1.5, 20);
		public static final Codec<Perception> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(1.0, 128.0).optionalFieldOf("sight_range", DEFAULTS.sightRange).forGetter(Perception::sightRange),
			Codec.doubleRange(10.0, 360.0).optionalFieldOf("field_of_view", DEFAULTS.fieldOfView).forGetter(Perception::fieldOfView),
			Codec.doubleRange(0.0, 8.0).optionalFieldOf("near_sense", DEFAULTS.nearSense).forGetter(Perception::nearSense),
			Codec.intRange(1, 200).optionalFieldOf("behaviour_poll", DEFAULTS.behaviourPoll).forGetter(Perception::behaviourPoll)
		).apply(i, Perception::new));
	}

	public record Gathering(boolean terrainChanges, int cutStepTicks, int maxTreeLogs, int saplingsPerTree, int keepClearOfDeeds, boolean shearAndMilk,
		boolean harvestHives) {
		public static final Gathering DEFAULTS = new Gathering(true, 30, 320, 2, 8, true, true);
		public static final Codec<Gathering> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("terrain_changes", DEFAULTS.terrainChanges).forGetter(Gathering::terrainChanges),
			Codec.intRange(1, 400).optionalFieldOf("cut_step_ticks", DEFAULTS.cutStepTicks).forGetter(Gathering::cutStepTicks),
			Codec.intRange(4, 4096).optionalFieldOf("max_tree_logs", DEFAULTS.maxTreeLogs).forGetter(Gathering::maxTreeLogs),
			Codec.intRange(1, 4).optionalFieldOf("saplings_per_tree", DEFAULTS.saplingsPerTree).forGetter(Gathering::saplingsPerTree),
			Codec.intRange(0, 64).optionalFieldOf("keep_clear_of_deeds", DEFAULTS.keepClearOfDeeds).forGetter(Gathering::keepClearOfDeeds),
			Codec.BOOL.optionalFieldOf("shear_and_milk", DEFAULTS.shearAndMilk).forGetter(Gathering::shearAndMilk),
			Codec.BOOL.optionalFieldOf("harvest_hives", DEFAULTS.harvestHives).forGetter(Gathering::harvestHives)
		).apply(i, Gathering::new));
	}

	public record Caravans(boolean embodied, int maxActive, double speed, int guards, int traders, int animals) {
		public static final Caravans DEFAULTS = new Caravans(true, 48, 1.6, 2, 1, 3);
		public static final Codec<Caravans> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("embodied", DEFAULTS.embodied).forGetter(Caravans::embodied),
			Codec.intRange(0, 1024).optionalFieldOf("max_active", DEFAULTS.maxActive).forGetter(Caravans::maxActive),
			Codec.doubleRange(0.1, 10.0).optionalFieldOf("speed", DEFAULTS.speed).forGetter(Caravans::speed),
			Codec.intRange(0, 6).optionalFieldOf("guards", DEFAULTS.guards).forGetter(Caravans::guards),
			Codec.intRange(1, 4).optionalFieldOf("traders", DEFAULTS.traders).forGetter(Caravans::traders),
			Codec.intRange(0, 8).optionalFieldOf("animals", DEFAULTS.animals).forGetter(Caravans::animals)
		).apply(i, Caravans::new));
	}

	public record Escort(double teleportDistance, int loseAfter, double arriveDistance) {
		public static final Escort DEFAULTS = new Escort(20.0, 1200, 5.0);
		public static final Codec<Escort> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(6.0, 128.0).optionalFieldOf("teleport_distance", DEFAULTS.teleportDistance).forGetter(Escort::teleportDistance),
			Codec.intRange(20, 72000).optionalFieldOf("lose_after", DEFAULTS.loseAfter).forGetter(Escort::loseAfter),
			Codec.doubleRange(1.0, 32.0).optionalFieldOf("arrive_distance", DEFAULTS.arriveDistance).forGetter(Escort::arriveDistance)
		).apply(i, Escort::new));
	}

	public record Embodiment(int spawnRadius, int despawnRadius, int maxPerSettlement, int maxPerServer, int spawnsPerTick,
		int passInterval, int settlementsPerPass) {
		public static final Embodiment DEFAULTS = new Embodiment(96, 112, 48, 400, 4, 10, 4);
		public static final Codec<Embodiment> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(8, 512).optionalFieldOf("spawn_radius", DEFAULTS.spawnRadius).forGetter(Embodiment::spawnRadius),
			Codec.intRange(8, 640).optionalFieldOf("despawn_radius", DEFAULTS.despawnRadius).forGetter(Embodiment::despawnRadius),
			Codec.intRange(0, 1024).optionalFieldOf("max_per_settlement", DEFAULTS.maxPerSettlement).forGetter(Embodiment::maxPerSettlement),
			Codec.intRange(0, 10000).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Embodiment::maxPerServer),
			Codec.intRange(1, 64).optionalFieldOf("spawns_per_tick", DEFAULTS.spawnsPerTick).forGetter(Embodiment::spawnsPerTick),
			Codec.intRange(1, 200).optionalFieldOf("pass_interval", DEFAULTS.passInterval).forGetter(Embodiment::passInterval),
			Codec.intRange(1, 64).optionalFieldOf("settlements_per_pass", DEFAULTS.settlementsPerPass).forGetter(Embodiment::settlementsPerPass)
		).apply(i, Embodiment::new));
	}

	public record Ai(int thinkNear, int thinkMid, int thinkFar, int nearDistance, int midDistance, int repathCooldown, int builderStepTicks,
		boolean scaffolding) {
		public static final Ai DEFAULTS = new Ai(2, 5, 12, 24, 64, 40, 30, true);
		public static final Codec<Ai> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 100).optionalFieldOf("think_near", DEFAULTS.thinkNear).forGetter(Ai::thinkNear),
			Codec.intRange(1, 100).optionalFieldOf("think_mid", DEFAULTS.thinkMid).forGetter(Ai::thinkMid),
			Codec.intRange(1, 200).optionalFieldOf("think_far", DEFAULTS.thinkFar).forGetter(Ai::thinkFar),
			Codec.intRange(1, 256).optionalFieldOf("near_distance", DEFAULTS.nearDistance).forGetter(Ai::nearDistance),
			Codec.intRange(1, 512).optionalFieldOf("mid_distance", DEFAULTS.midDistance).forGetter(Ai::midDistance),
			Codec.intRange(1, 400).optionalFieldOf("repath_cooldown", DEFAULTS.repathCooldown).forGetter(Ai::repathCooldown),
			Codec.intRange(1, 400).optionalFieldOf("builder_step_ticks", DEFAULTS.builderStepTicks).forGetter(Ai::builderStepTicks),
			Codec.BOOL.optionalFieldOf("scaffolding", DEFAULTS.scaffolding).forGetter(Ai::scaffolding)
		).apply(i, Ai::new));
	}

	public record Threats(boolean hostilesTargetFolk, boolean deathMessages) {
		public static final Threats DEFAULTS = new Threats(true, true);
		public static final Codec<Threats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("hostiles_target_folk", DEFAULTS.hostilesTargetFolk).forGetter(Threats::hostilesTargetFolk),
			Codec.BOOL.optionalFieldOf("death_messages", DEFAULTS.deathMessages).forGetter(Threats::deathMessages)
		).apply(i, Threats::new));
	}

	public record Talk(boolean greetings, int markerRefresh, boolean landmarkRumours) {
		public static final Talk DEFAULTS = new Talk(true, 40, true);
		public static final Codec<Talk> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("greetings", DEFAULTS.greetings).forGetter(Talk::greetings),
			Codec.intRange(1, 1200).optionalFieldOf("marker_refresh", DEFAULTS.markerRefresh).forGetter(Talk::markerRefresh),
			Codec.BOOL.optionalFieldOf("landmark_rumours", DEFAULTS.landmarkRumours).forGetter(Talk::landmarkRumours)
		).apply(i, Talk::new));
	}
}
