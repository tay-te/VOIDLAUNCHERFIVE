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
 * </pre>
 */
public record NpcConfig(Embodiment embodiment, Ai ai, Threats threats, Talk talk) {
	public static final NpcConfig DEFAULTS = new NpcConfig(Embodiment.DEFAULTS, Ai.DEFAULTS, Threats.DEFAULTS, Talk.DEFAULTS);

	public static final Codec<NpcConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Embodiment.CODEC.optionalFieldOf("embodiment", Embodiment.DEFAULTS).forGetter(NpcConfig::embodiment),
		Ai.CODEC.optionalFieldOf("ai", Ai.DEFAULTS).forGetter(NpcConfig::ai),
		Threats.CODEC.optionalFieldOf("threats", Threats.DEFAULTS).forGetter(NpcConfig::threats),
		Talk.CODEC.optionalFieldOf("talk", Talk.DEFAULTS).forGetter(NpcConfig::talk)
	).apply(i, NpcConfig::new));

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
