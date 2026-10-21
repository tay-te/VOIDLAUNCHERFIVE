package dev.voidmc.hearth.npc.crowd;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/crowds.json} (docs/LIVING_WORLD_2.md §5). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record CrowdConfig(Crowds crowds, Barks barks, Shutters shutters, Brawls brawls) {
	public static final CrowdConfig DEFAULTS = new CrowdConfig(Crowds.DEFAULTS, Barks.DEFAULTS, Shutters.DEFAULTS, Brawls.DEFAULTS);

	public static final Codec<CrowdConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Crowds.CODEC.optionalFieldOf("crowds", Crowds.DEFAULTS).forGetter(CrowdConfig::crowds),
		Barks.CODEC.optionalFieldOf("barks", Barks.DEFAULTS).forGetter(CrowdConfig::barks),
		Shutters.CODEC.optionalFieldOf("shutters", Shutters.DEFAULTS).forGetter(CrowdConfig::shutters),
		Brawls.CODEC.optionalFieldOf("brawls", Brawls.DEFAULTS).forGetter(CrowdConfig::brawls)
	).apply(i, CrowdConfig::new));

	/**
	 * @param enabled crowds react to players and to what happens in town
	 * @param passTicks how often the stimuli round each player are looked at
	 * @param maxReactingPerPlayer folk reacting to one player at a time
	 * @param reactionCooldownSeconds a folk reacts to the same stimulus at most this often
	 */
	public record Crowds(boolean enabled, int passTicks, int maxReactingPerPlayer, int reactionCooldownSeconds) {
		public static final Crowds DEFAULTS = new Crowds(true, 10, 12, 60);
		public static final Codec<Crowds> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Crowds::enabled),
			Codec.intRange(1, 200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Crowds::passTicks),
			Codec.intRange(0, 64).optionalFieldOf("max_reacting_per_player", DEFAULTS.maxReactingPerPlayer).forGetter(Crowds::maxReactingPerPlayer),
			Codec.intRange(0, 3600).optionalFieldOf("reaction_cooldown_seconds", DEFAULTS.reactionCooldownSeconds).forGetter(Crowds::reactionCooldownSeconds)
		).apply(i, Crowds::new));
	}

	/**
	 * @param perFolkSeconds a folk says a bark at most this often (section 0.2)
	 * @param perPlayerTownSeconds a player hears a bark in a town at most this often
	 * @param perLineSeconds the same line is said at most this often
	 */
	public record Barks(int perFolkSeconds, int perPlayerTownSeconds, int perLineSeconds) {
		public static final Barks DEFAULTS = new Barks(60, 4, 600);
		public static final Codec<Barks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 3600).optionalFieldOf("per_folk_seconds", DEFAULTS.perFolkSeconds).forGetter(Barks::perFolkSeconds),
			Codec.intRange(0, 600).optionalFieldOf("per_player_town_seconds", DEFAULTS.perPlayerTownSeconds).forGetter(Barks::perPlayerTownSeconds),
			Codec.intRange(0, 86400).optionalFieldOf("per_line_seconds", DEFAULTS.perLineSeconds).forGetter(Barks::perLineSeconds)
		).apply(i, Barks::new));
	}

	/**
	 * @param enabled shutters close at night, and while an outlaw, a raid or a storm is about
	 * @param perTick shutters a town opens or closes in one tick at most (shrinks as the server slows)
	 */
	public record Shutters(boolean enabled, int perTick) {
		public static final Shutters DEFAULTS = new Shutters(true, 32);
		public static final Codec<Shutters> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Shutters::enabled),
			Codec.intRange(1, 512).optionalFieldOf("per_tick", DEFAULTS.perTick).forGetter(Shutters::perTick)
		).apply(i, Shutters::new));
	}

	/**
	 * @param enabled folk may come to blows (feuds, tavern nights) when another module asks ({@code CrowdService.brawl})
	 * @param fineBits what a player who joined a brawl with bare fists pays the guard who breaks it up (brawling, not
	 *                 assault: docs/LIVING_WORLD_2.md §5.1)
	 * @param maxSeconds a brawl nobody breaks up ends after this long
	 * @param maxPerTown brawls in one town at once
	 */
	public record Brawls(boolean enabled, int fineBits, int maxSeconds, int maxPerTown) {
		public static final Brawls DEFAULTS = new Brawls(true, 5, 60, 1);
		public static final Codec<Brawls> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Brawls::enabled),
			Codec.intRange(0, 10_000).optionalFieldOf("fine_bits", DEFAULTS.fineBits).forGetter(Brawls::fineBits),
			Codec.intRange(5, 600).optionalFieldOf("max_seconds", DEFAULTS.maxSeconds).forGetter(Brawls::maxSeconds),
			Codec.intRange(0, 8).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Brawls::maxPerTown)
		).apply(i, Brawls::new));
	}
}
