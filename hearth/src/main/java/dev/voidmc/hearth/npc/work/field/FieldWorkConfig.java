package dev.voidmc.hearth.npc.work.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code config/hearth/fieldwork.json} (docs/LIVING_WORLD_2.md §6). Every field is optional, so an old file keeps working
 * when fields are added. Caps are the most at a comfortable tick time and shrink as the server slows
 * ({@link dev.voidmc.hearth.sim.LoadCaps}).
 */
public record FieldWorkConfig(Sites sites, Flocks flocks, Boats boats, Displays displays) {
	public static final FieldWorkConfig DEFAULTS = new FieldWorkConfig(Sites.DEFAULTS, Flocks.DEFAULTS, Boats.DEFAULTS, Displays.DEFAULTS);

	public static final Codec<FieldWorkConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Sites.CODEC.optionalFieldOf("sites", Sites.DEFAULTS).forGetter(FieldWorkConfig::sites),
		Flocks.CODEC.optionalFieldOf("flocks", Flocks.DEFAULTS).forGetter(FieldWorkConfig::flocks),
		Boats.CODEC.optionalFieldOf("boats", Boats.DEFAULTS).forGetter(FieldWorkConfig::boats),
		Displays.CODEC.optionalFieldOf("displays", Displays.DEFAULTS).forGetter(FieldWorkConfig::displays)
	).apply(i, FieldWorkConfig::new));

	/**
	 * @param enabled remote work sites (pastures, clamps, the Great Mine) are worked and embodied
	 * @param embodyDistance a remote site is embodied within this many blocks of a player
	 * @param passTicks how often remote sites round each player are looked at
	 */
	public record Sites(boolean enabled, int embodyDistance, int passTicks) {
		public static final Sites DEFAULTS = new Sites(true, 96, 40);
		public static final Codec<Sites> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Sites::enabled),
			Codec.intRange(16, 256).optionalFieldOf("embody_distance", DEFAULTS.embodyDistance).forGetter(Sites::embodyDistance),
			Codec.intRange(1, 1200).optionalFieldOf("pass_ticks", DEFAULTS.passTicks).forGetter(Sites::passTicks)
		).apply(i, Sites::new));
	}

	/**
	 * @param herdPerHerder animals a herder keeps on a pasture
	 * @param maxPerPasture flock animals and dogs embodied on one pasture
	 * @param maxPerTown flock animals and dogs embodied for one town
	 * @param maxPerServer flock animals and dogs embodied server-wide
	 */
	public record Flocks(int herdPerHerder, int maxPerPasture, int maxPerTown, int maxPerServer) {
		public static final Flocks DEFAULTS = new Flocks(8, 8, 24, 96);
		public static final Codec<Flocks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("herd_per_herder", DEFAULTS.herdPerHerder).forGetter(Flocks::herdPerHerder),
			Codec.intRange(0, 64).optionalFieldOf("max_per_pasture", DEFAULTS.maxPerPasture).forGetter(Flocks::maxPerPasture),
			Codec.intRange(0, 256).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Flocks::maxPerTown),
			Codec.intRange(0, 2048).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Flocks::maxPerServer)
		).apply(i, Flocks::new));
	}

	/**
	 * @param maxPerTown fishing boats embodied for one town
	 * @param maxPerServer fishing boats embodied server-wide
	 */
	public record Boats(int maxPerTown, int maxPerServer) {
		public static final Boats DEFAULTS = new Boats(6, 24);
		public static final Codec<Boats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Boats::maxPerTown),
			Codec.intRange(0, 512).optionalFieldOf("max_per_server", DEFAULTS.maxPerServer).forGetter(Boats::maxPerServer)
		).apply(i, Boats::new));
	}

	/**
	 * @param maxPerStall item displays (wares) on one stall
	 * @param maxPerTown item displays in one town
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
}
