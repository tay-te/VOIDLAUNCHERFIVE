package dev.voidmc.hearth.npc.crowd;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/crowds.json} (docs/LIVING_WORLD_2.md §5). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record CrowdConfig(Crowds crowds, Barks barks) {
	public static final CrowdConfig DEFAULTS = new CrowdConfig(Crowds.DEFAULTS, Barks.DEFAULTS);

	public static final Codec<CrowdConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Crowds.CODEC.optionalFieldOf("crowds", Crowds.DEFAULTS).forGetter(CrowdConfig::crowds),
		Barks.CODEC.optionalFieldOf("barks", Barks.DEFAULTS).forGetter(CrowdConfig::barks)
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
}
