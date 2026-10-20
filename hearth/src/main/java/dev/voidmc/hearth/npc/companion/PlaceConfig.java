package dev.voidmc.hearth.npc.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/place.json} (docs/LIVING_WORLD_2.md §8). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record PlaceConfig(Companions companions, Household household, Commissions commissions) {
	public static final PlaceConfig DEFAULTS = new PlaceConfig(Companions.DEFAULTS, Household.DEFAULTS, Commissions.DEFAULTS);

	public static final Codec<PlaceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Companions.CODEC.optionalFieldOf("companions", Companions.DEFAULTS).forGetter(PlaceConfig::companions),
		Household.CODEC.optionalFieldOf("household", Household.DEFAULTS).forGetter(PlaceConfig::household),
		Commissions.CODEC.optionalFieldOf("commissions", Commissions.DEFAULTS).forGetter(PlaceConfig::commissions)
	).apply(i, PlaceConfig::new));

	/**
	 * @param perPlayer companions a player may hire (section 13: 2)
	 * @param maxPerServer companions embodied server-wide
	 */
	public record Companions(int perPlayer, int maxPerServer) {
		public static final Companions DEFAULTS = new Companions(2, 64);
		public static final Codec<Companions> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 8).optionalFieldOf("per_player", DEFAULTS.perPlayer).forGetter(Companions::perPlayer),
			Codec.intRange(0, 1024).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Companions::maxPerServer)
		).apply(i, Companions::new));
	}

	/**
	 * @param servantsPerHouse servants a player's house may keep (by its beds)
	 */
	public record Household(int servantsPerHouse) {
		public static final Household DEFAULTS = new Household(3);
		public static final Codec<Household> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 16).optionalFieldOf("servants_per_house", DEFAULTS.servantsPerHouse).forGetter(Household::servantsPerHouse)
		).apply(i, Household::new));
	}

	/**
	 * @param knighthood the top rank of a lord's commissions is a household knighthood (section 13: yes)
	 */
	public record Commissions(boolean knighthood) {
		public static final Commissions DEFAULTS = new Commissions(true);
		public static final Codec<Commissions> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("knighthood", DEFAULTS.knighthood).forGetter(Commissions::knighthood)
		).apply(i, Commissions::new));
	}
}
